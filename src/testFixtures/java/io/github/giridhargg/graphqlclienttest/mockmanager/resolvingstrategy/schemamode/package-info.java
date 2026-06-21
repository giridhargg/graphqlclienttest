/**
 * Lazy, per-{@code (type, field)} resolver-function response stubbing — registered via
 * {@code MockGraphQlServer.resolveFrom(PerFieldResolverRegistry)}.
 *
 * <p>Each resolver is a {@link io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode.Resolver},
 * a function from {@code graphql.schema.DataFetchingEnvironment} to a value — closely mirroring
 * the shape of a real {@code @SchemaMapping}-annotated controller method. A resolver registered
 * for {@code (typeName, fieldName)} applies to <em>every</em> instance of that type encountered
 * while resolving a response, however many times it appears or however deeply nested, matching
 * how a real schema-mapped resolver behaves in production.</p>
 *
 * <p>Unlike {@link io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode},
 * resolvers here have full access to {@code DataFetchingEnvironment.getSource()} (the parent's
 * already-resolved value), {@code getArguments()}, and {@code getGraphQlContext()} — so a field's
 * value can be computed from its parent or from incoming arguments, rather than only being a
 * pre-computed static value.</p>
 *
 * <p>Build a registry via
 * {@link io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode.SchemaNode#builder()}.
 * A resolver function may return plain data, a {@code graphql.GraphQLError}, a
 * {@code Throwable}, or a reactive {@code Publisher}/{@code Mono}/{@code Flux} (for async fields
 * or subscriptions) — the same value contract as the {@code graphtree} strategy's leaf values,
 * including automatic error path/location enrichment.</p>
 */
package io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode;