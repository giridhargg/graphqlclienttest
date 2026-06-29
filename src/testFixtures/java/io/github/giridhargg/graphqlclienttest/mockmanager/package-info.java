/**
 * The core, transport-agnostic consumer-facing API: {@code MockGraphQlServer} and the queueing/
 * dispatch machinery behind it.
 *
 * <p>Everything in this package is shared identically between the two testing styles this library
 * offers — in-memory unit tests ({@link io.github.giridhargg.graphqlclienttest.httpgraphqlclient})
 * and real-local-server integration tests ({@link
 * io.github.giridhargg.graphqlclienttest.graphqlserverconfig}). Neither transport implementation
 * contains any stubbing logic of its own; both simply route every field resolution through {@link
 * io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer#resolve}.
 *
 * <h2>Core types</h2>
 *
 * <ul>
 *   <li>{@link io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer} — the bean
 *       consumer tests inject; registers stubbed responses and is consulted by the GraphQL engine
 *       for every field
 *   <li>{@link io.github.giridhargg.graphqlclienttest.mockmanager.ResolvingManager} — the FIFO
 *       queue of {@link io.github.giridhargg.graphqlclienttest.mockmanager.ExecutionRecord}s and
 *       the dispatch logic that picks the right resolution strategy per queued record
 *   <li>{@link io.github.giridhargg.graphqlclienttest.mockmanager.ExecutionRecord} — a sealed
 *       hierarchy describing one queued response, in one of two shapes (see {@link
 *       io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy})
 *   <li>{@link io.github.giridhargg.graphqlclienttest.mockmanager.ResolvingErrors} factory methods
 *       for simulating GraphQL-spec and Spring GraphQL {@code ErrorType} errors, respectively, with
 *       automatic path/location enrichment
 * </ul>
 *
 * @see io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy
 */
@NullMarked
package io.github.giridhargg.graphqlclienttest.mockmanager;

import org.jspecify.annotations.NullMarked;
