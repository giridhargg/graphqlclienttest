/**
 * Wiring for full integration tests against a real, locally running Spring GraphQL server.
 *
 * <p>Use this package's entry point,
 * {@link io.github.giridhargg.graphqlclienttest.graphqlserverconfig.EnableGraphQlServerTestConfiguration @EnableGraphQlServerTestConfiguration}, when consumer test code cannot substitute its own
 * and the test instead needs to intercept requests at the network boundary (typically via a proxy such
 * as WireMock pointed at the local server this package starts).</p>
 *
 * <p>This is the heavier-weight counterpart to
 * {@link io.github.giridhargg.graphqlclienttest.httpgraphqlclient}, which serves requests entirely
 * in-memory with no real server or network involved. Both expose the same consumer-facing
 * {@link io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer} stubbing API.</p>
 *
 * <p><strong>Required:</strong> the beans in this package only activate under the Spring profile
 * named {@code "integrationTest"} — test classes must declare
 * {@code @ActiveProfiles("integrationTest")} alongside {@code @EnableGraphQlServerTestConfiguration},
 * or none of this wiring is registered.</p>
 *
 * @see io.github.giridhargg.graphqlclienttest.graphqlserverconfig.RuntimeWiringTestAutoConfiguration
 */
package io.github.giridhargg.graphqlclienttest.graphqlserverconfig;