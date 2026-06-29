/**
 * In-memory test support for the synchronous, blocking {@code
 * org.springframework.graphql.client.HttpSyncGraphQlClient}.
 *
 * <p>Use this package's entry point, {@link
 * io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient.HttpSyncGraphQlClientTest @HttpSyncGraphQlClientTest},
 * for fast unit/component tests that exercise an {@code HttpSyncGraphQlClient} bean directly. Every
 * outbound request from that client is intercepted and resolved entirely in-memory — no real server
 * is started and no network call is made.
 *
 * <p>This mirrors {@link io.github.giridhargg.graphqlclienttest.httpgraphqlclient} (the reactive
 * {@code HttpGraphQlClient} equivalent) bean-for-bean and shares its consumer-facing {@link
 * io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer} stubbing API, built on
 * {@code RestClient}/{@code ClientHttpRequestInterceptor} instead of {@code WebClient}/{@code
 * ClientHttpConnector}.
 *
 * <p><strong>Subscriptions are not supported.</strong> {@code HttpSyncGraphQlClient} is built on a
 * strictly request-response transport with no streaming capability — its request spec does not
 * expose a subscription method at all. This is an inherent limitation of the blocking transport
 * itself, not something this library can work around. Use {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient} if your tests need subscription
 * support.
 *
 * @see
 *     io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient.HttpSyncGraphQlClientTestAutoConfiguration
 */
@NullMarked
package io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient;

import org.jspecify.annotations.NullMarked;
