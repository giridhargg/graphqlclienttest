/**
 * The two ways to describe a stubbed GraphQL response that supports nested partial success/error,
 * grouped here for discoverability. Each subpackage corresponds to one {@link
 * io.github.giridhargg.graphqlclienttest.mockmanager.ExecutionRecord} variant and one overload of
 * {@code MockGraphQlServer.resolveFrom(...)}.
 *
 * <ul>
 *   <li>{@link io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.graphnode} — a
 *       static, typed tree mirroring the response shape. Simpler to write for fixed fixtures;
 *       cannot express cross-field dependencies (e.g. a nested field's value depending on its
 *       parent's resolved value).
 * </ul>
 *
 * <p>Both strategies share the same automatic GraphQL error path/location enrichment (see {@link
 * io.github.giridhargg.graphqlclienttest.mockmanager.GraphQlErrors} and {@link
 * io.github.giridhargg.graphqlclienttest.mockmanager.SpringGraphQlErrors}) and the same FIFO
 * request-queueing semantics described on {@link
 * io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer}.
 */
@NullMarked
package io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy;

import org.jspecify.annotations.NullMarked;
