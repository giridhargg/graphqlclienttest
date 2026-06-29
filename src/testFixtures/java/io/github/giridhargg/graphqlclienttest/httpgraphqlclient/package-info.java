/**
 * In-memory test support for the reactive {@code
 * org.springframework.graphql.client.HttpGraphQlClient}.
 *
 * <p>Use this package's entry point, {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest @HttpGraphQlClientTest},
 * for fast unit tests that exercise an {@code HttpGraphQlClient} bean directly. Every outbound
 * request from that client is intercepted and resolved entirely in-memory — no real server is
 * started and no network call is made, including for {@code subscription} operations, which are
 * framed as synthetic {@code text/event-stream} responses.
 *
 * <p>This is the lighter-weight counterpart to {@link
 * io.github.giridhargg.graphqlclienttest.graphqlserverconfig}, which starts a real local Spring
 * GraphQL server for tests that need to exercise the consumer's own networking stack unmodified.
 * Both expose the same consumer-facing {@link
 * io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer} stubbing API.
 *
 * @see
 *     io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTestAutoConfiguration
 */
@NullMarked
package io.github.giridhargg.graphqlclienttest.httpgraphqlclient;

import org.jspecify.annotations.NullMarked;
