/**
 * Small pieces of JUnit/Spring TestContext glue shared by both testing styles this library offers.
 *
 * <p>Currently holds only
 * {@link io.github.giridhargg.graphqlclienttest.shared.MockGraphQlServerResetExtension}, which
 * resets every {@code MockGraphQlServer} bean's queued expectations before each test method —
 * registered automatically by both
 * {@link io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest @HttpGraphQlClientTest}
 * and
 * {@link io.github.giridhargg.graphqlclienttest.graphqlserverconfig.EnableGraphQlServerTestConfiguration @EnableGraphQlServerTestConfiguration},
 * so consumers do not need {@code @DirtiesContext} or manual reset calls to avoid expectation bleed between test
 * methods.</p>
 */
package io.github.giridhargg.graphqlclienttest.shared;