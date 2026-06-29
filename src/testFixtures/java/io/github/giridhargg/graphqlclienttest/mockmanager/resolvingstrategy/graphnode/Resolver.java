package io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.graphnode;

import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

/**
 * A lazy, per-field resolver function - the Option B equivalent of a production data fetcher,
 * scoped to a test.
 *
 * <p>Implementations may return: - plain data (Map, ListNode, scalar, POJO) - a {@link
 * graphql.GraphQLError} (will be wrapped as a field-level error) - a {@link Throwable} (will be
 * thrown, producing an INTERNAL_ERROR by default) - a {@link graphql.execution.DataFetcherResult}
 * (passed through as-is) - a {@link reactor.core.publisher.Mono}/{@link
 * reactor.core.publisher.Flux} for async/subscription fields
 */
@FunctionalInterface
public interface Resolver {

  @Nullable Object resolve(DataFetchingEnvironment environment) throws Throwable;
}
