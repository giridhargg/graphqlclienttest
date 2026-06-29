package io.github.giridhargg.graphqlclienttest.graphqlserverconfig;

import graphql.ExecutionResult;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Advances {@link MockGraphQlServer}'s queued expectations once per completed request, so that
 * consumer tests can call {@code resolveFrom(...)} (or any other registration method) multiple
 * times in a single test method to stub multiple sequential outbound GraphQL requests — for
 * example, paginated fetches where the consumer's code under test makes several round-trips within
 * a single inbound request scope.
 *
 * <h2>Timing for queries and mutations</h2>
 *
 * <p>For {@code query}/{@code mutation} operations, the queue advances as soon as the response is
 * produced — equivalently, after all data fetchers for that request have completed (success or
 * GraphQL-level error).
 *
 * <h2>Timing for subscriptions</h2>
 *
 * <p>Subscriptions cannot be advanced eagerly: a {@code WebGraphQlResponse} for a subscription is
 * emitted as soon as the event {@code Publisher} is established, which is <em>before</em> any
 * individual event (and therefore before any nested-field data fetcher) has actually resolved.
 * Polling at that point would prematurely drop the queued expectation while nested fields are still
 * being resolved against it. Instead, {@link #tapSubscriptionCompletion} wraps the response's event
 * stream so the queue only advances once that stream completes or errors — i.e. once the
 * subscription itself is torn down, not once per emitted event.
 *
 * <h2>Transport-level failures</h2>
 *
 * <p>If {@code chain.next(request)} itself errors (a failure before any {@code WebGraphQlResponse}
 * is produced at all), the queue is still advanced via {@code doOnError}, so a single broken
 * request cannot stall a queue of otherwise-unrelated expectations for the rest of the test.
 */
class ExecutionRecordPollingInterceptor implements WebGraphQlInterceptor {

  private final ObjectProvider<MockGraphQlServer> mockGraphQlServerObjectProvider;

  ExecutionRecordPollingInterceptor(
      ObjectProvider<MockGraphQlServer> mockGraphQlServerObjectProvider) {
    this.mockGraphQlServerObjectProvider = mockGraphQlServerObjectProvider;
  }

  @Override
  public @NonNull Mono<WebGraphQlResponse> intercept(
      @NonNull WebGraphQlRequest request, @NonNull Chain chain) {

    return chain
        .next(request)
        .flatMap(
            response -> {
              if (isSubscription(request)) {
                return Mono.just(tapSubscriptionCompletion(response));
              }
              pollExecutionRecord();
              return Mono.just(response);
            })
        .doOnError(_ -> pollExecutionRecord());
  }

  /**
   * Wraps a subscription response's event {@link Publisher} so that {@link
   * MockGraphQlServer#pollExecutionRecord()} fires once the stream terminates (completion or
   * error), not when the stream is first established.
   */
  private WebGraphQlResponse tapSubscriptionCompletion(WebGraphQlResponse response) {
    Publisher<ExecutionResult> originalPublisher = response.getExecutionResult().getData();
    Flux<ExecutionResult> tapped =
        Flux.from(originalPublisher).doFinally(_ -> pollExecutionRecord());
    return response.transform(builder -> builder.data(tapped));
  }

  private void pollExecutionRecord() {
    mockGraphQlServerObjectProvider.getObject().pollExecutionRecord();
  }

  /**
   * Determines whether the selected operation in {@code request} is a {@code subscription}, by
   * parsing the document and resolving the operation by name (falling back to the sole operation
   * when the document defines only one and no name was given, per the GraphQL spec).
   */
  private boolean isSubscription(WebGraphQlRequest request) {
    var document = request.getDocument();
    if (document == null || document.isBlank()) return false;
    try {
      var operationName = request.getOperationName();
      return new graphql.parser.Parser()
          .parseDocument(document).getDefinitions().stream()
              .filter(graphql.language.OperationDefinition.class::isInstance)
              .map(graphql.language.OperationDefinition.class::cast)
              .filter(
                  op ->
                      operationName == null
                          || operationName.isBlank()
                          || operationName.equals(op.getName()))
              .findFirst()
              .map(
                  op ->
                      op.getOperation()
                          == graphql.language.OperationDefinition.Operation.SUBSCRIPTION)
              .orElse(false);
    } catch (Exception ignored) {
      return false;
    }
  }
}
