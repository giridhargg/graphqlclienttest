/**
 * Typed, nested-tree response stubbing — registered via {@code
 * MockGraphQlServer.resolveFrom(GraphNode)}.
 *
 * <p>{@link
 * io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.graphnode.GraphNode} is a
 * sealed tree (object / list / leaf) mirroring the shape of the GraphQL response, with the root
 * operation field itself included as the top-level object's first key — e.g. for {@code { bookById
 * { id author { bio } } } }, the tree is {@code GraphNode.of("bookById", GraphNode.of("id", "...",
 * "author", GraphNode.of("bio", ...)))}.
 *
 * <h2>Partial success and partial error</h2>
 *
 * <p>Any leaf may hold plain data, a {@code graphql.GraphQLError}, or a {@code Throwable} — so a
 * single tree can express a response where some fields resolve normally and sibling or nested
 * fields simultaneously carry GraphQL-spec errors, exactly like a real partial GraphQL response.
 * Errors are automatically enriched with the correct {@code path} and source {@code location} at
 * resolution time (see {@link io.github.giridhargg.graphqlclienttest.mockmanager.GraphQlErrors} /
 * {@link io.github.giridhargg.graphqlclienttest.mockmanager.SpringGraphQlErrors}); consumers never
 * need to specify a path by hand.
 *
 * <h2>Resolution performance</h2>
 *
 * <p>Field resolution does <em>not</em> re-walk the tree from its root for every field. Once a
 * field resolves to a child {@code ObjectNode}/{@code ListNode}, that node itself becomes the
 * {@code DataFetchingEnvironment} source for its children, so each subsequent field lookup is an
 * O(1) step from its immediate parent rather than an O(depth) walk from the tree root.
 *
 * <h2>When to prefer this over per-field resolvers</h2>
 *
 * <p>Use this strategy when the whole response can be described as a fixed, static value — the
 * common case.
 */
@NullMarked
package io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.graphnode;

import org.jspecify.annotations.NullMarked;
