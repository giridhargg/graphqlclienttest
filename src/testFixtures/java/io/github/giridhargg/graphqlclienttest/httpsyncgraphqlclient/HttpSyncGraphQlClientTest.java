package io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient;

import io.github.giridhargg.graphqlclienttest.common.MockGraphQlServerResetExtension;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Enables in-memory GraphQL client testing for {@link
 * org.springframework.graphql.client.HttpSyncGraphQlClient} consumers, without starting any local
 * server or making any real network call.
 *
 * <p>This is the synchronous, blocking counterpart to {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest @HttpGraphQlClientTest}
 * — same consumer-facing stubbing API, same schema discovery rules, same persisted-query support,
 * built on {@link org.springframework.web.client.RestClient} instead of {@code WebClient}. Annotate
 * a {@code @SpringBootTest} (or plain unit) test class with this annotation to get:
 *
 * <ul>
 *   <li>An autowirable {@code HttpSyncGraphQlClient} bean, backed entirely by an in-memory {@code
 *       graphql-java} engine
 *   <li>An autowirable {@link io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer}
 *       bean used to stub responses and errors per test
 *   <li>Automatic per-test reset of all queued stubs (no expectation bleed between test methods,
 *       even without {@code @DirtiesContext})
 * </ul>
 *
 * <pre>
 * {
 *   &#64;code
 *   &#64;HttpSyncGraphQlClientTest
 *   class BookClientTest {
 *
 *     &#64;Autowired
 *     MockGraphQlServer graphQlServer;
 *     &#64;Autowired
 *     HttpSyncGraphQlClient graphQlClient;
 *
 *     @Test
 *     void fetchesBook() {
 *       graphQlServer
 *           .resolveFrom(GraphNode.of("bookById", GraphNode.of("id", "1", "name", "Dune")));
 *       var response = graphQlClient.document("{ bookById(id:\"1\") { id name } }").execute();
 *       assertThat(response.field("bookById.name").toEntity(String.class)).isEqualTo("Dune");
 *     }
 *   }
 * }
 * </pre>
 *
 * <h2>Subscriptions are not supported</h2>
 *
 * <p>{@code HttpSyncGraphQlClient} is built on a strictly request-response transport contract and
 * has no {@code retrieveSubscription}/{@code executeSubscription} method at all. If your tests need
 * subscription support, use {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest @HttpGraphQlClientTest}
 * (reactive {@code HttpGraphQlClient}) instead — everything else about this annotation's stubbing
 * API works identically between the two.
 *
 * <h2>Schema discovery</h2>
 *
 * <p>By default, the GraphQL schema is auto-discovered from any {@code *.graphqls}/{@code
 * *.graphql} file on the test classpath. Override the search location via {@link #properties()} if
 * your schema lives somewhere non-standard, e.g.:
 *
 * <pre>{@code
 * &#64;HttpSyncGraphQlClientTest(properties = "graphql.test.assets.schema-location=classpath:my-schema.graphqls")
 * }</pre>
 *
 * <h2>Persisted queries</h2>
 *
 * <p>Persisted query support is entirely optional. See the project README for the file naming
 * convention and configuration properties.
 *
 * @see io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer
 * @see HttpSyncGraphQlClientTestAutoConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith({SpringExtension.class, MockGraphQlServerResetExtension.class})
@Import(HttpSyncGraphQlClientTestAutoConfiguration.class)
@TestPropertySource
public @interface HttpSyncGraphQlClientTest {

  /**
   * Additional configuration classes to import alongside this annotation's own auto-configuration.
   */
  @AliasFor(annotation = Import.class, attribute = "value")
  Class<?>[] classes() default {};

  /**
   * Additional property source locations to load, merged with the library's bundled defaults.
   * Consumer-supplied values for a given key take precedence over the library defaults.
   */
  @AliasFor(annotation = TestPropertySource.class, attribute = "locations")
  String[] propertySourceLocations() default {"classpath:graphql-client-test-defaults.properties"};

  /**
   * Inline {@code key=value} property overrides. These take precedence over both the library
   * defaults and {@link #propertySourceLocations()}.
   *
   * <p>Example: {@code @HttpSyncGraphQlClientTest(properties =
   * "graphql.test.assets.schema-location=classpath:my-schema.graphqls")}
   */
  @AliasFor(annotation = TestPropertySource.class, attribute = "properties")
  String[] properties() default {};
}
