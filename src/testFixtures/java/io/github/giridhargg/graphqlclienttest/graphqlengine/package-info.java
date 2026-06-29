/**
 * SchemaNode loading and in-memory GraphQL execution.
 *
 * <p>This package owns two concerns that sit underneath both testing styles this library supports
 * (in-memory unit tests and real-local-server integration tests):
 *
 * <ul>
 *   <li>{@link io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets} —
 *       discovers and compiles the GraphQL schema, and loads persisted query assets, from the test
 *       classpath. Results are cached for the lifetime of the JVM, keyed by the resolved resource
 *       locations, so repeated test classes with identical configuration do not re-parse the
 *       schema.
 *   <li>{@link io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlTestExecutor} — builds a
 *       {@code graphql-java} {@code GraphQL} instance from the compiled schema, with every field of
 *       every object type routed through {@code MockGraphQlServer}, and executes queries,
 *       mutations, and subscriptions against it entirely in-memory (used by the in-memory {@code
 *       httpgraphqlclient} package; not used by {@code graphqlserverconfig}, which instead wires
 *       the same unified-fetcher pattern into a real, locally running Spring GraphQL server).
 * </ul>
 *
 * <p>Neither class here is normally referenced directly by consumer test code — they are
 * implementation details wired automatically by {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest @HttpGraphQlClientTest}
 * and {@link
 * io.github.giridhargg.graphqlclienttest.graphqlserverconfig.EnableGraphQlServerTestConfiguration @EnableGraphQlServerTestConfiguration}.
 */
@NullMarked
package io.github.giridhargg.graphqlclienttest.graphqlengine;

import org.jspecify.annotations.NullMarked;
