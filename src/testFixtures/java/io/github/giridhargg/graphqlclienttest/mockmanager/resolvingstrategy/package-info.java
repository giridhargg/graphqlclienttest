/**
 * The two ways to describe a stubbed GraphQL response that supports nested partial
 * success/error, grouped here for discoverability. Each subpackage corresponds to one
 * {@link io.github.giridhargg.graphqlclienttest.mockmanager.ExecutionRecord} variant and one
 * overload of {@code MockGraphQlServer.resolveFrom(...)}.
 *
 * <ul>
 *   <li>{@link io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode} —
 *       a static, typed tree mirroring the response shape. Simpler to write for fixed fixtures;
 *       cannot express cross-field dependencies (e.g. a nested field's value depending on its
 *       parent's resolved value).</li>
 *   <li>{@link io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode} —
 *       lazy, per-{@code (type, field)} resolver functions with full
 *       {@code DataFetchingEnvironment} access (arguments, parent source, context), mirroring how
 *       a real {@code @SchemaMapping} resolver behaves. More powerful, slightly more verbose for
 *       simple cases.</li>
 * </ul>
 *
 * <p>Both strategies share the same automatic GraphQL error path/location enrichment (see
 * {@link io.github.giridhargg.graphqlclienttest.mockmanager.GraphQlErrors} and
 * {@link io.github.giridhargg.graphqlclienttest.mockmanager.SpringGraphQlErrors}) and the same
 * FIFO request-queueing semantics described on
 * {@link io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer}.</p>
 */
package io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy;