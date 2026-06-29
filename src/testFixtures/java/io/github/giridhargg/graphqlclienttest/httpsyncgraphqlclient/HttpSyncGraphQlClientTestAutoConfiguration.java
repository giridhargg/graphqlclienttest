package io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient;

import static io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient.Constants.APQ_REGISTRY;
import static io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient.Constants.GRAPHQL_STATIC_TEST_ASSETS;
import static io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient.Constants.GRAPHQL_TEST_EXECUTOR;
import static io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient.Constants.HTTP_SYNC_GRAPHQL_TEST_CLIENT;
import static io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient.Constants.MOCK_GRAPHQL_SERVER;
import static io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient.Constants.REST_CLIENT_TEST_BUILDER;
import static io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient.Constants.REST_TEST_CLIENT;

import io.github.giridhargg.graphqlclienttest.graphqlengine.ApqRegistry;
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
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the in-memory {@link MockGraphQlServer} infrastructure for synchronous, blocking {@link
 * HttpSyncGraphQlClient} consumers.
 *
 * <p>This configuration is imported automatically by {@link
 * HttpSyncGraphQlClientTest @HttpSyncGraphQlClientTest} and is not normally referenced directly by
 * consumers. The resulting {@code HttpSyncGraphQlClient} bean is wired against a {@link RestClient}
 * whose {@link org.springframework.http.client.ClientHttpRequestInterceptor} resolves every
 * outbound request entirely in-memory against an embedded {@code graphql-java} engine (see {@link
 * InMemorySyncMockGraphQlServer}) — no real network call is made and no local Spring GraphQL server
 * is started.
 *
 * <p>This mirrors {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTestAutoConfiguration}
 * bean-for-bean, with {@link RestClient}/{@link RestClient.Builder} substituted for {@code
 * WebClient}/{@code WebClient.Builder} and a blocking {@code ClientHttpRequestInterceptor}
 * substituted for the reactive {@code ClientHttpConnector}. The same {@code @DependsOn} ordering
 * rationale applies: {@code GraphQlTestExecutor} must compile the schema and register the unified
 * data fetcher before {@code MockGraphQlServer} attaches its interceptor to {@code
 * RestClient.Builder}, since the interceptor registration mutates the builder by reference.
 *
 * <p><strong>Subscriptions are not supported</strong> through this configuration — see {@link
 * InMemorySyncMockGraphQlServer} for why this is an inherent limitation of {@code
 * HttpSyncGraphQlClient}'s blocking transport, not something this library can work around.
 *
 * <p>Property resolution (schema location, persisted query locations) is owned entirely by {@link
 * HttpSyncGraphQlClientTest @HttpSyncGraphQlClientTest} via its {@code @TestPropertySource}
 * composition; this class intentionally does not declare its own property source so there is
 * exactly one place controlling precedence.
 *
 * @see HttpSyncGraphQlClientTest
 * @see InMemorySyncMockGraphQlServer
 */
@TestConfiguration
@Import(ObjectMapper.class)
@EnableConfigurationProperties(GraphQlStaticTestAssetsProperties.class)
class HttpSyncGraphQlClientTestAutoConfiguration {

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

  /**
   * Base {@link RestClient.Builder} that the in-memory interceptor attaches itself to.
   *
   * <p>The base URL is a placeholder; no request ever actually reaches the network because {@link
   * #mockGraphQlServiceServer} registers an interceptor that short-circuits the chain before this
   * builder is used to construct the final {@link RestClient}.
   */
  @Primary
  @Bean(name = REST_CLIENT_TEST_BUILDER)
  RestClient.Builder restClientTestBuilder() {
    return RestClient.builder().baseUrl("https://spring.io/graphql");
  }

  /**
   * Loads and caches the GraphQL schema and persisted query assets for this test context, based on
   * {@link GraphQlStaticTestAssetsProperties}.
   */
  @Primary
  @Bean(GRAPHQL_STATIC_TEST_ASSETS)
  GraphQlStaticTestAssets.Assets assets(GraphQlStaticTestAssetsProperties properties) {
    return GraphQlStaticTestAssets.forPaths(properties);
  }

  /**
   * Builds the in-memory {@code graphql-java} execution engine, with every field of every type in
   * the schema wired to route through {@link MockGraphQlServer}.
   */
  @Primary
  @Bean(GRAPHQL_TEST_EXECUTOR)
  @DependsOn(GRAPHQL_STATIC_TEST_ASSETS)
  GraphQlTestExecutor graphQlTestExecutor(
      ObjectProvider<MockGraphQlServer> mockGraphQlServerProvider,
      @Qualifier(GRAPHQL_STATIC_TEST_ASSETS) GraphQlStaticTestAssets.Assets grapQlAssets) {
    return new GraphQlTestExecutor(mockGraphQlServerProvider, grapQlAssets);
  }

  /**
   * Creates the {@link MockGraphQlServer} and registers its in-memory {@code
   * ClientHttpRequestInterceptor} on {@link #restClientTestBuilder()}. This is the bean consumer
   * tests inject to register expectations via {@code resolveFrom(...)}.
   */
  @Primary
  @Bean(MOCK_GRAPHQL_SERVER)
  @DependsOn({REST_CLIENT_TEST_BUILDER, GRAPHQL_TEST_EXECUTOR})
  MockGraphQlServer mockGraphQlServiceServer(
      @Qualifier(REST_CLIENT_TEST_BUILDER) RestClient.Builder restClientBuilder,
      @Qualifier(GRAPHQL_TEST_EXECUTOR) GraphQlTestExecutor graphQlTestExecutor,
      @Qualifier(APQ_REGISTRY) ApqRegistry apqRegistry,
      ObjectMapper objectMapper) {
    return InMemorySyncMockGraphQlServer.createServer(
        restClientBuilder, graphQlTestExecutor, apqRegistry, objectMapper);
  }

  /**
   * Builds the final {@link RestClient}. Must run after {@link #mockGraphQlServiceServer} so the
   * builder already carries the in-memory interceptor.
   */
  @Primary
  @Bean(REST_TEST_CLIENT)
  @DependsOn(MOCK_GRAPHQL_SERVER)
  RestClient restTestClient(@Qualifier(REST_CLIENT_TEST_BUILDER) RestClient.Builder builder) {
    return builder.build();
  }

  /**
   * The {@link HttpSyncGraphQlClient} consumer test code should autowire and exercise directly.
   * Every {@code document(...)} call this client makes is served entirely in-memory.
   */
  @Primary
  @Bean(HTTP_SYNC_GRAPHQL_TEST_CLIENT)
  @DependsOn(REST_TEST_CLIENT)
  HttpSyncGraphQlClient httpSyncGraphQlTestClient(
      @Qualifier(REST_TEST_CLIENT) RestClient restClient) {
    return HttpSyncGraphQlClient.builder(restClient).build();
  }
}
