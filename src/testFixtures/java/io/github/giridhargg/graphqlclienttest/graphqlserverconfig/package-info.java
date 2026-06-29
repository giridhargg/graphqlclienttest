/**
 * Wiring for full integration tests against a real, locally running Spring GraphQL server.
 *
 * <p>Use this package's entry point, {@link
 * io.github.giridhargg.graphqlclienttest.graphqlserverconfig.EnableGraphQlServerTestConfiguration @EnableGraphQlServerTestConfiguration},
 * when consumer test code cannot substitute its own and the test instead needs to intercept
 * requests at the network boundary (typically via a proxy such as WireMock pointed at the local
 * server this package starts).
 *
 * <p>This is the heavier-weight counterpart to {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient}, which serves requests entirely
 * in-memory with no real server or network involved. Both expose the same consumer-facing {@link
 * io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer} stubbing API.
 *
 * @see
 *     io.github.giridhargg.graphqlclienttest.graphqlserverconfig.RuntimeWiringTestAutoConfiguration
 */
@NullMarked
package io.github.giridhargg.graphqlclienttest.graphqlserverconfig;

import org.jspecify.annotations.NullMarked;
