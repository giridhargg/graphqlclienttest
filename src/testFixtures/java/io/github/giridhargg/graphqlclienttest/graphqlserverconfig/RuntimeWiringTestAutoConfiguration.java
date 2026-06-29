package io.github.giridhargg.graphqlclienttest.graphqlserverconfig;

import static io.github.giridhargg.graphqlclienttest.graphqlserverconfig.Constants.APQ_REGISTRY;
import static io.github.giridhargg.graphqlclienttest.graphqlserverconfig.Constants.GRAPHQL_STATIC_TEST_ASSETS;
import static io.github.giridhargg.graphqlclienttest.graphqlserverconfig.Constants.MOCK_GRAPHQL_RESOLVER;
import static io.github.giridhargg.graphqlclienttest.graphqlserverconfig.Constants.PERSISTED_QUERY_INTERCEPTOR;
import static io.github.giridhargg.graphqlclienttest.graphqlserverconfig.Constants.RUNTIME_WIRING_CONFIGURER;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLObjectType;
import io.github.giridhargg.graphqlclienttest.graphqlengine.ApqRegistry;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssetsProperties;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.WebGraphQlInterceptor;

/**
 * Wires a {@link MockGraphQlServer}-backed {@link RuntimeWiringConfigurer} into a real, locally
 * running Spring GraphQL server, for use by {@link
 * EnableGraphQlServerTestConfiguration @EnableGraphQlServerTestConfiguration}.
 *
 * <p>As with {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTestAutoConfiguration},
 * the {@link RuntimeWiringConfigurer} built here registers the unified mock-backed {@link
 * DataFetcher} for <em>every</em> field of <em>every</em> non-introspection object type in the
 * compiled schema, not just root operation types. Consumers must not register their own
 * {@code @SchemaMapping} controllers against this schema.
 *
 * <h2>Persisted query support and response-queue draining</h2>
 *
 * <p>This configuration also registers two {@link WebGraphQlInterceptor}s:
 *
 * <ul>
 *   <li>{@link PersistedQueryInterceptor} — resolves {@code extensions.persistedQuery.sha256Hash}
 *       requests to their stored document text before execution
 *   <li>{@link ExecutionRecordPollingInterceptor} — advances the {@code MockGraphQlServer}'s queued
 *       expectation after each request completes (or, for subscriptions, after the event stream
 *       terminates), so consumers can queue multiple {@code resolveFrom(...)} calls per test for
 *       multiple sequential outbound requests (e.g. pagination)
 * </ul>
 */
@TestConfiguration
@EnableConfigurationProperties(GraphQlStaticTestAssetsProperties.class)
class RuntimeWiringTestAutoConfiguration {

  /** Creates the {@link MockGraphQlServer} consumer tests inject to register expectations. */
  @Primary
  @Bean(MOCK_GRAPHQL_RESOLVER)
  MockGraphQlServer mockGraphQlServer() {
    return MockGraphQlServer.createServer();
  }

  /** See {@link ExecutionRecordPollingInterceptor}. */
  @Primary
  @Bean(Constants.EXECUTION_POLLING_INTERCEPTOR)
  ExecutionRecordPollingInterceptor executionRecordPollingInterceptor(
      ObjectProvider<MockGraphQlServer> mockGraphQlServerObjectProvider) {
    return new ExecutionRecordPollingInterceptor(mockGraphQlServerObjectProvider);
  }

  /** Loads and caches the GraphQL schema and persisted query assets for this test context. */
  @Primary
  @Bean(GRAPHQL_STATIC_TEST_ASSETS)
  GraphQlStaticTestAssets.Assets assets(GraphQlStaticTestAssetsProperties properties) {
    return GraphQlStaticTestAssets.forPaths(properties);
  }

  /**
   * The {@link ApqRegistry} that handles Automatic Persisted Query (APQ) two-phase handshake at
   * runtime. Cleared before each test method by {@link
   * io.github.giridhargg.graphqlclienttest.common.MockGraphQlServerResetExtension}.
   */
  @Primary
  @Bean(APQ_REGISTRY)
  ApqRegistry apqRegistry() {
    return new ApqRegistry();
  }

  /** See {@link PersistedQueryInterceptor}. */
  @Primary
  @Bean(PERSISTED_QUERY_INTERCEPTOR)
  WebGraphQlInterceptor persistedQueryInterceptor(
      @Qualifier(GRAPHQL_STATIC_TEST_ASSETS) GraphQlStaticTestAssets.Assets assets,
      @Qualifier(APQ_REGISTRY) ApqRegistry apqRegistry) {
    return new PersistedQueryInterceptor(assets, apqRegistry);
  }

  /**
   * Registers the library's unified {@link MockGraphQlServer}-backed fetcher for all schema fields
   * that are not already claimed by a consumer {@code @Controller} method.
   *
   * <h2>Why GraphQlSourceBuilderCustomizer, not RuntimeWiringConfigurer</h2>
   *
   * <p>All previous approaches ({@code defaultDataFetcher}, {@code WiringFactory}) failed because
   * they ran at the same time as {@code AnnotatedControllerConfigurer}, with no reliable way to
   * inspect which fields it had already claimed. The solution is to run <em>after</em> all {@link
   * RuntimeWiringConfigurer}s — including {@code AnnotatedControllerConfigurer} — have finished
   * building the schema, and then post-process the compiled {@link graphql.schema.GraphQLSchema}
   * directly.
   *
   * <p>{@link org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer}
   * provides exactly this hook via {@code schemaFactory}: it receives the fully-built {@link
   * graphql.schema.idl.TypeDefinitionRegistry} and {@link graphql.schema.idl.RuntimeWiring} after
   * all configurers have run, and returns a customised {@link graphql.schema.GraphQLSchema}. At
   * that point the schema's {@link graphql.schema.GraphQLCodeRegistry} already contains all
   * controller {@code DataFetcher} registrations, and we can use {@link
   * graphql.schema.GraphQLCodeRegistry.Builder#dataFetcherIfAbsent} to fill in our unified fetcher
   * only for coordinates that have no existing registration — guaranteed non-interference with
   * controllers.
   *
   * <h2>Connection to the user's suggestion</h2>
   *
   * <p>This is the same pattern used in {@link
   * io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlTestExecutor}: compile the schema,
   * then transform its {@code CodeRegistry} to inject the unified fetcher at the right coordinates.
   * The difference is that here we do it inside a {@code GraphQlSourceBuilderCustomizer} so Spring
   * Boot's own {@code GraphQlSource} infrastructure owns the schema lifecycle, rather than us
   * building a separate {@code graphql.GraphQL} instance.
   */
  @Primary
  @Bean(RUNTIME_WIRING_CONFIGURER)
  @DependsOn({MOCK_GRAPHQL_RESOLVER, GRAPHQL_STATIC_TEST_ASSETS})
  GraphQlSourceBuilderCustomizer runtimeWiringTestConfigurer(
      @Qualifier(MOCK_GRAPHQL_RESOLVER) MockGraphQlServer mockGraphQlServer) {

    DataFetcher<?> unifiedFetcher =
        dfe -> {
          try {
            return mockGraphQlServer.resolve(dfe);
          } catch (Throwable e) {
            throw new RuntimeException(e);
          }
        };

    return builder ->
        builder.schemaFactory(
            (typeDefinitionRegistry, runtimeWiring) -> {
              // Build the schema exactly as Spring GraphQL would normally
              var schema =
                  new graphql.schema.idl.SchemaGenerator()
                      .makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

              // Now the CodeRegistry already contains all @QueryMapping/@SchemaMapping fetchers
              // from AnnotatedControllerConfigurer. Use dataFetcherIfAbsent to fill in the above
              // unified fetcher only for coordinates with no existing registration.
              var newCodeRegistry =
                  schema
                      .getCodeRegistry()
                      .transform(
                          registry -> {
                            for (var type : schema.getAllTypesAsList()) {
                              if (type instanceof GraphQLObjectType objectType
                                  && !objectType.getName().startsWith("__")) {
                                for (var field : objectType.getFieldDefinitions()) {
                                  var fieldCoordinates =
                                      graphql.schema.FieldCoordinates.coordinates(
                                          objectType.getName(), field.getName());
                                  registry.dataFetcherIfAbsent(fieldCoordinates, unifiedFetcher);
                                }
                              }
                            }
                          });

              return schema.transform(b -> b.codeRegistry(newCodeRegistry));
            });
  }
}
