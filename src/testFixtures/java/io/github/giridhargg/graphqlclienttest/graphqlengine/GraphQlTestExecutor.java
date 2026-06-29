package io.github.giridhargg.graphqlclienttest.graphqlengine;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLObjectType;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestComponent;
import reactor.core.publisher.Flux;

/**
 * Executes GraphQL queries, mutations, and subscriptions entirely in-memory against a {@code
 * graphql-java} engine built from the compiled schema, with every field of every object type in the
 * schema routed through {@link MockGraphQlServer}.
 *
 * <p>This is the engine that powers the in-memory client-side connector (see {@code
 * InMemoryMockGraphQlServer}). It is not normally used directly by consumer test code — interact
 * with {@link MockGraphQlServer} instead.
 *
 * <h2>Why every field, not just root fields</h2>
 *
 * <p>The unified data fetcher is registered for <em>every</em> field of <em>every</em>
 * non-introspection {@link GraphQLObjectType} in the schema, not just {@code Query}/{@code
 * Mutation}/{@code Subscription}. This is required to support nested partial success/error stubbing
 * (see the {@code graphtree} and {@code perfieldresolvers} resolution strategies) and means
 * consumers must not register their own {@code @SchemaMapping} resolvers against this same compiled
 * schema — this library owns resolution for every field once a test class is wired with it.
 */
@TestComponent
public class GraphQlTestExecutor {

  private final GraphQL graphQL;
  private final GraphQlStaticTestAssets.Assets assets;
  private final ObjectProvider<MockGraphQlServer> mockGraphQlServerProvider;

  /**
   * Builds the in-memory engine: registers a single unified {@link DataFetcher} for every field of
   * every object type in the compiled schema, delegating each invocation to {@link
   * MockGraphQlServer#resolve}.
   *
   * @param mockGraphQlServerProvider lazily resolved to break the circular dependency between this
   *     executor and the {@code MockGraphQlServer} bean that wraps it
   * @param assets the compiled schema and persisted query assets for this test context
   * @throws IllegalStateException if no schema was found at the configured location
   */
  public GraphQlTestExecutor(
      ObjectProvider<MockGraphQlServer> mockGraphQlServerProvider,
      GraphQlStaticTestAssets.Assets assets) {
    this.assets = assets;
    this.mockGraphQlServerProvider = mockGraphQlServerProvider;

    var baseSchema = assets.compiledSchema();
    if (baseSchema == null) {
      throw new IllegalStateException(
          "GraphQlTestExecutor requires a GraphQl schema but none was found. "
              + "Ensure a graphql schema file exists at the configured location "
              + "{graphql.test.assets.schema-location}");
    }

    // registers all types
    var newCodeRegistry =
        baseSchema
            .getCodeRegistry()
            .transform(
                registry -> {
                  for (var type : baseSchema.getAllTypesAsList()) {
                    if (type instanceof GraphQLObjectType objectType
                        && !objectType.getName().startsWith("__")) { // skip introspection types
                      for (var field : objectType.getFieldDefinitions()) {
                        registry.dataFetcher(
                            FieldCoordinates.coordinates(objectType.getName(), field.getName()),
                            (DataFetcher<?>)
                                dfe -> {
                                  try {
                                    return mockGraphQlServerProvider.getObject().resolve(dfe);
                                  } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                  }
                                });
                      }
                    }
                  }
                });

    this.graphQL =
        GraphQL.newGraphQL(baseSchema.transform(builder -> builder.codeRegistry(newCodeRegistry)))
            .build();
  }

  /**
   * Executes a {@code query} or {@code mutation} document with no variables, operation name, or
   * extensions.
   */
  public Map<String, Object> executeQuery(String query, @Nullable Map<String, Object> variables) {
    return executeQuery(query, variables, null, null);
  }

  /**
   * Executes a {@code query} or {@code mutation} document, selecting a specific operation by name
   * when the document contains more than one.
   */
  public Map<String, Object> executeQuery(
      String query, @Nullable Map<String, Object> variables, @Nullable String operationName) {
    return executeQuery(query, variables, operationName, null);
  }

  /**
   * Executes a {@code query} or {@code mutation} document and returns the response as a
   * GraphQL-spec map (i.e. {@code {"data": ..., "errors": [...]}}).
   *
   * @param query the GraphQL document text (already resolved if it was a persisted query)
   * @param variables query variables, or {@code null} if none
   * @param operationName required when {@code query} contains multiple operation definitions;
   *     otherwise may be {@code null}
   * @param extensions forwarded to {@link ExecutionInput#getExtensions()}; useful for resolvers
   *     that branch on protocol extensions
   */
  public Map<String, Object> executeQuery(
      String query,
      @Nullable Map<String, Object> variables,
      @Nullable String operationName,
      @Nullable Map<String, Object> extensions) {
    var builder = ExecutionInput.newExecutionInput().query(query);
    if (variables != null) builder.variables(variables);
    if (operationName != null) builder.operationName(operationName);
    if (extensions != null) builder.extensions(extensions);
    return executeInput(builder.build());
  }

  /** Executes a {@code subscription} document with no variables, operation name, or extensions. */
  public Flux<Map<String, Object>> executeSubscription(
      String query, @Nullable Map<String, Object> variables) {
    return executeSubscription(query, variables, null, null);
  }

  /**
   * Executes a {@code subscription} document, selecting a specific operation by name when the
   * document contains more than one.
   */
  public Flux<Map<String, Object>> executeSubscription(
      String query, @Nullable Map<String, Object> variables, @Nullable String operationName) {
    return executeSubscription(query, variables, operationName, null);
  }

  /**
   * Executes a {@code subscription} document and returns one GraphQL-spec response map per emitted
   * source event. Response-level extensions registered on the current queue head are applied to
   * every emitted event's {@link ExecutionResult}.
   */
  public Flux<Map<String, Object>> executeSubscription(
      String query,
      @Nullable Map<String, Object> variables,
      @Nullable String operationName,
      @Nullable Map<String, Object> extensions) {
    var builder = ExecutionInput.newExecutionInput().query(query);
    if (variables != null) builder.variables(variables);
    if (operationName != null) builder.operationName(operationName);
    if (extensions != null) builder.extensions(extensions);
    var executionResult = graphQL.execute(builder.build());
    Publisher<ExecutionResult> source = executionResult.getData();
    var responseExtensions = mockGraphQlServerProvider.getObject().peekResponseExtensions();
    return Flux.from(source)
        .map(result -> applyExtensions(result, responseExtensions).toSpecification());
  }

  /**
   * Executes a pre-built {@link ExecutionInput} directly, applying any response-level extensions
   * queued on {@code MockGraphQlServer} before serialising.
   */
  public Map<String, Object> executeInput(ExecutionInput executionInput) {
    var result = graphQL.execute(executionInput);
    var responseExtensions = mockGraphQlServerProvider.getObject().peekResponseExtensions();
    return applyExtensions(result, responseExtensions).toSpecification();
  }

  /**
   * Applies {@code responseExtensions} to {@code result} when non-null and non-empty, otherwise
   * returns the original unchanged. This is a pure transformation — the queue is not advanced here.
   */
  private ExecutionResult applyExtensions(
      ExecutionResult result, @Nullable Map<Object, Object> responseExtensions) {
    if (responseExtensions == null || responseExtensions.isEmpty()) {
      return result;
    }
    return result.transform(b -> b.extensions(responseExtensions));
  }

  /** Returns the compiled schema and persisted query assets backing this executor. */
  public GraphQlStaticTestAssets.Assets getGraphQlStaticAssets() {
    return assets;
  }
}
