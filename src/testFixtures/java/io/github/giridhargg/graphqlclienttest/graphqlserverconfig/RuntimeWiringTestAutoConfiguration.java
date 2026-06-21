package io.github.giridhargg.graphqlclienttest.graphqlserverconfig;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLObjectType;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssetsProperties;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.WebGraphQlInterceptor;

/**
 * Wires a {@link MockGraphQlServer}-backed {@link RuntimeWiringConfigurer} into a real, locally
 * running Spring GraphQL server, for use by
 * {@link EnableGraphQlServerTestConfiguration @EnableGraphQlServerTestConfiguration}.
 *
 * <p><b>Important:</b> this configuration is only activated under the Spring profile named
 * {@code "integrationTest"} (see {@link Profile @Profile} below). Test classes using
 * {@code @EnableGraphQlServerTestConfiguration} must activate that exact profile — typically via
 * {@code @ActiveProfiles("integrationTest")} — or none of these beans will be registered and the
 * local GraphQL server will run with no data fetchers wired in.</p>
 *
 * <p>As with {@link io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTestAutoConfiguration},
 * the {@link RuntimeWiringConfigurer} built here registers the unified mock-backed
 * {@link DataFetcher} for <em>every</em> field of <em>every</em> non-introspection object type in
 * the compiled schema, not just root operation types. Consumers must not register their own
 * {@code @SchemaMapping} controllers against this schema.</p>
 *
 * <h2>Persisted query support and response-queue draining</h2>
 * <p>This configuration also registers two {@link WebGraphQlInterceptor}s:</p>
 * <ul>
 *   <li>{@link PersistedQueryTestInterceptor} — resolves {@code extensions.persistedQuery.sha256Hash}
 *       requests to their stored document text before execution</li>
 *   <li>{@link ExecutionRecordPollingInterceptor} — advances the {@code MockGraphQlServer}'s queued
 *       expectation after each request completes (or, for subscriptions, after the event stream
 *       terminates), so consumers can queue multiple {@code resolveFrom(...)} calls per test for
 *       multiple sequential outbound requests (e.g. pagination)</li>
 * </ul>
 */
@TestConfiguration
@Profile("integrationTest")
@EnableConfigurationProperties(GraphQlStaticTestAssetsProperties.class)
class RuntimeWiringTestAutoConfiguration {

    /** Creates the {@link MockGraphQlServer} consumer tests inject to register expectations. */
    @Primary
    @Bean(Constants.MOCK_GRAPHQL_RESOLVER)
    MockGraphQlServer mockGraphQlServer() {
        return MockGraphQlServer.createServer();
    }

    /** See {@link ExecutionRecordPollingInterceptor}. */
    @Primary
    @Bean(Constants.EXECUTION_POLLING_INTERCEPTOR)
    ExecutionRecordPollingInterceptor executionRecordPollingInterceptor(ObjectProvider<MockGraphQlServer> mockGraphQlServerObjectProvider) {
        return new ExecutionRecordPollingInterceptor(mockGraphQlServerObjectProvider);
    }

    /** Loads and caches the GraphQL schema and persisted query assets for this test context. */
    @Primary
    @Bean(Constants.GRAPHQL_STATIC_TEST_ASSETS)
    GraphQlStaticTestAssets.Assets assets(GraphQlStaticTestAssetsProperties properties) {
        return GraphQlStaticTestAssets.forPaths(properties);
    }

    /** See {@link PersistedQueryTestInterceptor}. */
    @Primary
    @Bean(Constants.PERSISTED_QUERY_INTERCEPTOR)
    WebGraphQlInterceptor persistedQueryInterceptor(GraphQlStaticTestAssetsProperties properties) {
        return new PersistedQueryTestInterceptor(properties);
    }

    /**
     * Registers a single unified {@link DataFetcher} — delegating to
     * {@link MockGraphQlServer#resolve} — as the default data fetcher for every field of every
     * non-introspection object type in the compiled schema.
     */
    @Primary
    @Bean(Constants.RUNTIME_WIRING_CONFIGURER)
    @DependsOn({Constants.MOCK_GRAPHQL_RESOLVER, Constants.GRAPHQL_STATIC_TEST_ASSETS})
    RuntimeWiringConfigurer runtimeWiringTestConfigurer(
            @Qualifier(Constants.MOCK_GRAPHQL_RESOLVER)
            MockGraphQlServer mockGraphQlServer,
            @Qualifier(Constants.GRAPHQL_STATIC_TEST_ASSETS)
            GraphQlStaticTestAssets.Assets assets) {

        DataFetcher<?> unifiedFetcher = dfe -> {
            try {
                return mockGraphQlServer.resolve(dfe);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        return wiringBuilder -> {
            for (var type : assets.compiledSchema().getAllTypesAsList()) {
                if (type instanceof GraphQLObjectType objectType
                        && !objectType.getName().startsWith("__")) {
                    wiringBuilder.type(objectType.getName(),
                            typeWiring -> typeWiring.defaultDataFetcher(unifiedFetcher));
                }
            }
        };
    }
}
