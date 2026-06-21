package io.github.giridhargg.graphqlclienttest.graphqlserverconfig;

import io.github.giridhargg.graphqlclienttest.shared.MockGraphQlServerResetExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables full end-to-end GraphQL client testing for {@code @SpringBootTest}-style integration
 * tests, where a real Spring GraphQL server is started locally and the consumer's own
 * {@code WebClient}/{@code HttpGraphQlClient} (typically pointed at a network proxy such as
 * WireMock, which in turn forwards to the local server) is exercised unmodified.
 *
 * <p>Unlike {@link io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest @HttpGraphQlClientTest}
 * — which replaces the {@code ClientHttpConnector} so requests never leave
 * the JVM — this annotation leaves the consumer's networking stack completely untouched. It instead
 * wires a {@link RuntimeWiringTestAutoConfiguration unified data fetcher} into Spring GraphQL's own
 * {@code RuntimeWiringConfigurer} machinery, so that whatever real HTTP request the consumer's
 * application makes, the server side of that request is served by the same
 * {@link io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer} stubbing API used
 * for in-memory unit tests.</p>
 *
 * <p>This is the right choice when consumers cannot substitute their production
 * {@code WebClient}/{@code RestClient} configuration in tests (e.g. it is wired deep inside
 * business logic) and instead need to intercept the request at the network boundary.</p>
 *
 * - your {@code application-integrationTest.yml} should have these minimal properties. Notice this file name, that the spring profile must be '-integrationTest'
 * <pre>{@code
 * spring:
 *   graphql:
 *     schema:
 *       locations: classpath*:graphql/** # Location of GraphQl schema files
 *
 * # your graphql client should be configured with this structure
 * my_graphql_server:
 *   host: "http://localhost"
 *   port: ${wiremock.server.port}
 *   path: "/graphql"
 *
 * graphql:
 *   test:
 *     assets:
 *       schemaLocation: "classpath*:graphql/*.graphqls"
 * }</pre>
 *
 * - {@code Your GraphQl-Client configuration should follow this pattern}
 * <pre>{@code
 *     @Bean
 *     HttpGraphQlClient httpGraphQlClient(
 *             @Value("${my_graphql_server.host}") String host,
 *             @Value("${my_graphql_server.port}") String port,
 *             @Value("${my_graphql_server.path}") String path) {
 *         var baseUrl = String.format("%s:%s%s", host, port, path);
 *         var webClient = WebClient.create(baseUrl)
 *                 .mutate()
 *                 .build();
 *         return HttpGraphQlClient.create(webClient);
 *     }
 * }</pre>
 *
 * - {@code Your integration test class}
 * <pre>{@code
 * @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 * @EnableGraphQlServerTestConfiguration  // STEP 1: Bring graphql-server auto-configuration
 * @EnableWireMock(...) // wiremock server
 * class BookServiceIntegrationTest {
 *
 *     // STEP 2: Bring local spring boot server (starter by @SpringBootTest) port
 *     @LocalServerPort private int localServerPort;
 *
 *     @Autowired MockGraphQlServer graphQlServer;
 *
 *     @BeforeEach
 *     public void setup() {
 *         // STEP 3: Setup wiremock as proxy to forward the request to local spring-graphql server
 *         stubFor(
 *                 post(urlPathMatching("/graphql"))
 *                         .willReturn(
 *                                 aResponse()
 *                                         .proxiedFrom("http://localhost:" + localServerPort)));
 *     }
 *
 *     @Test
 *     void fetchesBook() {
 *         // STEP 4: stub resolvers
 *         graphQlServer.resolveFrom(QueryNode.of("bookById", QueryNode.of("id", "1")));
 *         // exercise the consumer's real controller / service / WebClient here
 *     }
 * }
 * }</pre>
 *
 * @see RuntimeWiringTestAutoConfiguration
 * @see ExecutionRecordPollingInterceptor
 * @see PersistedQueryTestInterceptor
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(RuntimeWiringTestAutoConfiguration.class)
@ExtendWith(MockGraphQlServerResetExtension.class)
public @interface EnableGraphQlServerTestConfiguration {
}
