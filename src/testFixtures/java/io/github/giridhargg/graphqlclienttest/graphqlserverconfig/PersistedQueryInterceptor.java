package io.github.giridhargg.graphqlclienttest.graphqlserverconfig;

import io.github.giridhargg.graphqlclienttest.graphqlengine.ApqRegistry;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import reactor.core.publisher.Mono;

/**
 * Implements the full <strong>Automatic Persisted Query (APQ)</strong> two-phase handshake as a
 * {@link WebGraphQlInterceptor}, covering both the static pre-loaded query map and the dynamic
 * runtime registration path.
 *
 * <h2>Protocol phases</h2>
 *
 * <h3>Phase 1 — hash-only (optimistic request)</h3>
 *
 * <p>The client sends {@code extensions.persistedQuery.sha256Hash} with no (or empty) {@code query}
 * field, expecting the server to already know the query:
 *
 * <ul>
 *   <li>If the hash is found in the static classpath assets or the dynamic {@link ApqRegistry}, the
 *       request is rewritten with the resolved query text and the chain continues normally.
 *   <li>If the hash is <em>not</em> found, the chain is <strong>short-circuited</strong> and a
 *       {@code PersistedQueryNotFound} error response is returned (without executing anything), so
 *       the client knows to retry with the full query (phase 2).
 * </ul>
 *
 * <h3>Phase 2 — hash + full query (registration request)</h3>
 *
 * <p>The client resends with both the {@code query} field and the hash extension. This interceptor
 * validates that {@code SHA-256(query) == hash}, registers the mapping in {@link ApqRegistry} for
 * future phase-1 requests, then continues the chain normally so the response is returned in the
 * same round-trip (no separate acknowledgement needed).
 *
 * <p>If the hash does not match the query body, a {@code PersistedQueryHashMismatch} error is
 * returned immediately.
 *
 * <h2>Pass-through (no APQ)</h2>
 *
 * <p>Requests that carry no {@code persistedQuery} extension at all pass through unchanged — APQ
 * support is purely additive.
 *
 * @see ApqRegistry
 * @see GraphQlStaticTestAssets
 */
class PersistedQueryInterceptor implements WebGraphQlInterceptor {

  // private static final String SHA256_HASH = "sha256Hash";
  // private static final String PERSISTED_QUERY = "persistedQuery";
  //
  //// private final Map<String, String> persistedQueries;
  //
  // PersistedQueryInterceptor(GraphQlStaticTestAssets.Assets assets) {
  // this.assets = assets.persistedQueries();
  // }
  //
  // /**
  // * @param properties resolves which persisted-graphNode-hashes file(s) and {@code .gql} document
  // * pattern to load; the actual loading is delegated to
  // * {@link GraphQlStaticTestAssets#forPaths}, which caches the result for the
  // * lifetime of the JVM, so repeated construction across test classes with the
  // * same configuration does not re-read the classpath.
  // */
  // PersistedQueryInterceptor(GraphQlStaticTestAssetsProperties properties) {
  // // even though this interceptor is being invoked for multiple requests, the persisted queries
  // are static assets
  // // that are loaded once at first test startup time and cached in memory for fast lookup on
  // subsequent requests
  // this.persistedQueries = GraphQlStaticTestAssets.forPaths(properties).persistedQueries();
  // }
  //
  // @Override
  // public @NonNull Mono<WebGraphQlResponse> intercept(@NonNull WebGraphQlRequest request, @NonNull
  // Chain chain) {
  // Map<String, Object> extensions = request.getExtensions();
  // Map<String, Object> persisted = extensions != null
  // ? (Map<String, Object>) extensions.get("persistedQuery") : null;
  //
  // if (persisted != null && persisted.get(SHA256_HASH) != null) {
  // return handlePersistedQuery(request, chain, persisted);
  // }
  // return chain.next(request);
  // }
  //
  // /**
  // * Rebuilds the request with its {@code graphNode} field replaced by the resolved persisted
  // graphNode
  // * document, then continues the chain.
  // */
  // private Mono<WebGraphQlResponse> handlePersistedQuery(
  // WebGraphQlRequest request, Chain chain, Map<String, Object> persisted) {
  // var hash = (String) persisted.get(SHA256_HASH);
  // var persistedQuery = persistedQueries.get(hash);
  // if (persistedQuery == null) {
  // return Mono.error(new GraphQLException("Persisted graphNode not found for hash: " + hash));
  // }
  // Map<String, Object> body = new HashMap<>();
  // body.put("graphNode", persistedQuery);
  // body.put("operationName", request.getOperationName());
  // body.put("variables", request.getVariables());
  // body.put("extensions", request.getExtensions());
  // var newRequest = new WebGraphQlRequest(
  // request.getUri().toUri(), request.getHeaders(), request.getCookies(),
  // request.getRemoteAddress(), request.getAttributes(),
  // body, request.getId(), request.getLocale());
  // return chain.next(newRequest);
  // }

  private static final String SHA256_HASH = "sha256Hash";
  private static final String PERSISTED_QUERY = "persistedQuery";

  private final GraphQlStaticTestAssets.Assets assets;
  private final ApqRegistry apqRegistry;

  public PersistedQueryInterceptor(GraphQlStaticTestAssets.Assets assets, ApqRegistry apqRegistry) {
    this.assets = assets;
    this.apqRegistry = apqRegistry;
  }

  @Override
  public @NonNull Mono<WebGraphQlResponse> intercept(
      @NonNull WebGraphQlRequest request, @NonNull Chain chain) {

    Map<String, Object> extensions = request.getExtensions();
    @SuppressWarnings("unchecked")
    Map<String, Object> persisted =
        extensions != null ? (Map<String, Object>) extensions.get(PERSISTED_QUERY) : null;

    if (persisted == null || persisted.get(SHA256_HASH) == null) {
      return chain.next(request); // no APQ extension - pass through unchanged
    }

    var hash = (String) persisted.get(SHA256_HASH);
    var query = request.getDocument();
    boolean hasQuery = query != null && !query.isBlank();

    if (hasQuery) {
      return handlePhase2(request, chain, hash, query);
    } else {
      return handlePhase1(request, chain, hash);
    }
  }

  /**
   * Phase 1: hash-only lookup. Returns {@code PersistedQueryNotFound} if unknown so the client
   * retries with the full query (phase 2), otherwise rewrites the request and continues the chain.
   */
  private Mono<WebGraphQlResponse> handlePhase1(
      WebGraphQlRequest request, Chain chain, String hash) {

    var result = apqRegistry.lookup(hash, assets);
    return switch (result) {
      case ApqRegistry.LookupResult.Found(var resolvedQuery) ->
          chain.next(rewriteWithQuery(request, resolvedQuery));
      case ApqRegistry.LookupResult.NotFound(var missingHash) ->
          Mono.just(persistedQueryNotFoundResponse(missingHash));
    };
  }

  /**
   * Phase 2: hash + query. Validates the hash matches the query body, registers it in the {@link
   * ApqRegistry}, then continues the chain normally (response returned immediately — no separate
   * registration acknowledgement).
   */
  private Mono<WebGraphQlResponse> handlePhase2(
      WebGraphQlRequest request, Chain chain, String hash, String query) {

    var result = apqRegistry.register(hash, query);
    return switch (result) {
      case ApqRegistry.RegistrationResult.Registered ignored ->
          chain.next(request); // query field is already present; no rewrite needed
      case ApqRegistry.RegistrationResult.HashMismatch(var claimed, var actual) ->
          Mono.just(hashMismatchResponse(claimed, actual));
    };
  }

  /**
   * Rebuilds the request with its {@code query} field replaced by the resolved persisted query
   * document, for use after a successful phase-1 lookup.
   */
  private WebGraphQlRequest rewriteWithQuery(WebGraphQlRequest request, String resolvedQuery) {
    Map<String, Object> body = new HashMap<>();
    body.put("query", resolvedQuery);
    body.put("operationName", request.getOperationName());
    body.put("variables", request.getVariables());
    body.put("extensions", request.getExtensions());
    return new WebGraphQlRequest(
        request.getUri().toUri(),
        request.getHeaders(),
        request.getCookies(),
        request.getRemoteAddress(),
        request.getAttributes(),
        body,
        request.getId(),
        request.getLocale());
  }

  /**
   * Builds a synthetic {@link WebGraphQlResponse} carrying a {@code PersistedQueryNotFound} GraphQL
   * error, telling the client to retry with the full query body (phase 2). No execution occurs.
   */
  private WebGraphQlResponse persistedQueryNotFoundResponse(String hash) {
    return buildErrorResponse(
        "PersistedQueryNotFound",
        Map.of("code", "PERSISTED_QUERY_NOT_FOUND", "persistedQueryId", hash));
  }

  /**
   * Builds a synthetic {@link WebGraphQlResponse} carrying a {@code PersistedQueryHashMismatch}
   * error when the client's claimed hash does not match the SHA-256 of the submitted query body.
   */
  private WebGraphQlResponse hashMismatchResponse(String claimedHash, String actualHash) {
    return buildErrorResponse(
        "PersistedQueryHashMismatch",
        Map.of(
            "code",
            "PERSISTED_QUERY_HASH_MISMATCH",
            "claimedHash",
            claimedHash,
            "actualHash",
            actualHash));
  }

  /**
   * Builds a synthetic {@link WebGraphQlResponse} carrying the given GraphQL error without
   * executing the query chain at all.
   *
   * <p>The public constructor chain is: {@code ExecutionInput + ExecutionResult →
   * DefaultExecutionGraphQlResponse → WebGraphQlResponse}. The {@code ExecutionInput} is a minimal
   * placeholder (only the error message is used as its query text); it is never actually executed.
   */
  @SuppressWarnings("unchecked")
  private WebGraphQlResponse buildErrorResponse(String message, Map<String, Object> extensions) {
    var graphQlError =
        graphql.GraphqlErrorBuilder.newError()
            .message(message)
            .extensions((Map<String, Object>) extensions)
            .build();

    var executionResult =
        graphql.ExecutionResultImpl.newExecutionResult().addError(graphQlError).build();

    // ExecutionInput is required by DefaultExecutionGraphQlResponse but is never
    // used for anything meaningful here - it just satisfies the constructor contract.
    var executionInput = graphql.ExecutionInput.newExecutionInput().query(message).build();

    var executionGraphQlResponse =
        new DefaultExecutionGraphQlResponse(executionInput, executionResult);
    return new WebGraphQlResponse(executionGraphQlResponse);
  }
}
