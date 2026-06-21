package io.github.giridhargg.graphqlclienttest.httpgraphqlclient;

import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssetsProperties;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlTestExecutor;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the in-memory {@link MockGraphQlServer} infrastructure for reactive, asynchronous
 * {@link HttpGraphQlClient} consumers.
 *
 * <p>This configuration is imported automatically by {@link HttpGraphQlClientTest @HttpGraphQlClientTest}
 * and is not normally referenced directly by consumers. The resulting {@code HttpGraphQlClient} bean
 * is wired against a {@link WebClient} whose {@link org.springframework.http.client.reactive.ClientHttpConnector}
 * executes every outbound request entirely in-memory against an embedded {@code graphql-java} engine
 * (see {@link InMemoryMockGraphQlServer}) — no real network call is made and no local Spring GraphQL
 * server is started.</p>
 *
 * <p>The bean wiring order is significant: {@code GraphQlTestExecutor} must compile the schema and
 * register the unified data fetcher before {@code MockGraphQlServer} attaches the in-memory
 * {@code ClientHttpConnector} to {@code WebClient.Builder}, since the connector captures the builder
 * by reference and mutates it in place. The {@code @DependsOn} chain below encodes that order.</p>
 *
 * <p>Property resolution (schema location, persisted query locations) is owned entirely by
 * {@link HttpGraphQlClientTest @HttpGraphQlClientTest} via its {@code @TestPropertySource} composition;
 * this class intentionally does not declare its own property source so there is exactly one place
 * controlling precedence.</p>
 *
 * @see HttpGraphQlClientTest
 * @see InMemoryMockGraphQlServer
 */
@TestConfiguration
@Import(ObjectMapper.class)
@EnableConfigurationProperties(GraphQlStaticTestAssetsProperties.class)
class HttpGraphQlClientTestAutoConfiguration {

    /**
     * Base {@link WebClient.Builder} that the in-memory connector attaches itself to.
     *
     * <p>The base URL is a placeholder; no request ever actually reaches the network because
     * {@link #mockGraphQlServiceServer} replaces the connector with an in-memory one before this
     * builder is used to construct the final {@link WebClient}.</p>
     */
    @Primary
    @Bean(name = Constants.WEB_CLIENT_TEST_BUILDER)
    WebClient.Builder webClientTestBuilder() {
        return WebClient.builder()
                .baseUrl("https://spring.io/graphql")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * Loads and caches the GraphQL schema and persisted query assets for this test context,
     * based on {@link GraphQlStaticTestAssetsProperties}.
     */
    @Primary
    @Bean(Constants.GRAPHQL_STATIC_TEST_ASSETS)
    GraphQlStaticTestAssets.Assets assets(GraphQlStaticTestAssetsProperties properties) {
        return GraphQlStaticTestAssets.forPaths(properties);
    }

    /**
     * Builds the in-memory {@code graphql-java} execution engine, with every field of every type
     * in the schema wired to route through {@link MockGraphQlServer}.
     */
    @Primary
    @Bean(Constants.GRAPHQL_TEST_EXECUTOR)
    @DependsOn(Constants.GRAPHQL_STATIC_TEST_ASSETS)
    GraphQlTestExecutor graphQlTestExecutor(
            ObjectProvider<MockGraphQlServer> mockGraphQlServerProvider,
            @Qualifier(Constants.GRAPHQL_STATIC_TEST_ASSETS)
            GraphQlStaticTestAssets.Assets grapQlAssets) {
        return new GraphQlTestExecutor(mockGraphQlServerProvider, grapQlAssets);
    }

    /**
     * Creates the {@link MockGraphQlServer} and attaches its in-memory {@code ClientHttpConnector}
     * to {@link #webClientTestBuilder()}. This is the bean consumer tests inject to register
     * request expectations via {@code expect(...)} and resolvers via {@code resolveFrom(...)}.
     */
    @Primary
    @Bean(Constants.MOCK_GRAPHQL_SERVER)
    @DependsOn({Constants.WEB_CLIENT_TEST_BUILDER, Constants.GRAPHQL_TEST_EXECUTOR})
    MockGraphQlServer mockGraphQlServiceServer(
            @Qualifier(Constants.WEB_CLIENT_TEST_BUILDER)
            WebClient.Builder webClientBuilder,
            @Qualifier(Constants.GRAPHQL_TEST_EXECUTOR)
            GraphQlTestExecutor graphQlTestExecutor,
            ObjectMapper objectMapper) {
        return InMemoryMockGraphQlServer.createServer(webClientBuilder, graphQlTestExecutor, objectMapper);
    }

    /**
     * Builds the final {@link WebClient}. Must run after {@link #mockGraphQlServiceServer} so the
     * builder already carries the in-memory connector.
     */
    @Primary
    @Bean(Constants.WEB_TEST_CLIENT)
    @DependsOn(Constants.MOCK_GRAPHQL_SERVER)
    WebClient webTestClient(@Qualifier(Constants.WEB_CLIENT_TEST_BUILDER) WebClient.Builder builder) {
        return builder.build();
    }

    /**
     * The {@link HttpGraphQlClient} consumer test code should autowire and exercise directly.
     * Every {@code document(...)} call this client makes is served entirely in-memory.
     */
    @Primary
    @Bean(Constants.HTTP_GRAPHQL_TEST_CLIENT)
    @DependsOn(Constants.WEB_TEST_CLIENT)
    HttpGraphQlClient httpGraphQlTestClient(@Qualifier(Constants.WEB_TEST_CLIENT) WebClient webClient) {
        return HttpGraphQlClient.builder(webClient)
                .interceptor(new PersistedQueryInterceptor())
                .build();
    }
}
