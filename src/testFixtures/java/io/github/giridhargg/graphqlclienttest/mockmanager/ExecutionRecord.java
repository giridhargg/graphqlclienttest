package io.github.giridhargg.graphqlclienttest.mockmanager;

import graphql.ExecutionInput;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode.QueryNode;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode.SchemaNodeRegistry;
import org.jspecify.annotations.Nullable;


/**
 * One queued stub registered via {@link MockGraphQlServer}, representing a single outbound
 * GraphQL request's worth of mocked resolution.
 *
 * <p>{@code ExecutionRecord}s are held in a FIFO queue inside {@link ResolvingManager}: each
 * registration call (e.g. {@code resolveFrom(...)}) appends one record, and each completed
 * outbound request consumes (polls) the head of the queue. This lets a single test method queue
 * multiple responses for multiple sequential outbound requests — for example, simulating
 * paginated fetches where the consumer under test makes several round-trips within one inbound
 * request scope.</p>
 *
 * <p>This is a sealed type with one variant per resolution strategy. Each variant carries only
 * the state relevant to its own strategy — there is no shared nullable-field guessing between
 * strategies, and {@link ResolvingManager}'s dispatch is an exhaustive {@code switch} over the
 * permitted subtypes, so adding a new strategy is a compile-time-checked change everywhere it
 * needs to be handled.</p>
 */
public sealed interface ExecutionRecord {

    /**
     * An optional expected {@link ExecutionInput}, used to assert on the shape of the actual
     * outbound request (query text, variables, operation name) before resolving a response for
     * it. {@code null} when the record was registered without a request-matching expectation.
     */
    @Nullable ExecutionInput executionInput();

    /**
     * How strictly {@link #executionInput()} should be compared against the actual request, or
     * {@code null} when {@link #executionInput()} is also {@code null}.
     */
    @Nullable MatchType matchType();

    /** Comparison strategy for {@link #executionInput()} against the actual outbound request. */
    enum MatchType {

        /** The actual request must equal the expected input exactly, field for field. */
        EXACT,

        /**
         * The actual request must contain at least everything present in the expected input;
         * fields left {@code null} on the expected input are not checked.
         */
        PARTIAL
    }

    /**
     * Resolves a response from a typed {@link QueryNode} tree mirroring the GraphQL response
     * shape, including the root field. Supports partial success/error at any nesting depth and
     * within list elements — see {@link QueryNode} for the full resolution model.
     */
    record GraphNodeRecord(
            @Nullable ExecutionInput executionInput,
            QueryNode queryNode,
            @Nullable MatchType matchType) implements ExecutionRecord {

        /** Convenience factory for registering a {@code GraphNodeRecord} record with no request matcher. */
        public static GraphNodeRecord of(QueryNode queryNode) {
            return new GraphNodeRecord(null, queryNode, null);
        }
    }

    /**
     * Resolves a response from per-{@code (typeName, fieldName)} resolver functions, mirroring
     * how a real {@code @SchemaMapping} resolver would be invoked — each registered resolver
     * receives the live {@code DataFetchingEnvironment} (arguments, parent source object,
     * context) and applies to every instance of that type encountered in the response, however
     * many times it appears.
     */
    record TypeNodeRecord(
            @Nullable ExecutionInput executionInput,
            SchemaNodeRegistry registry,
            @Nullable MatchType matchType) implements ExecutionRecord {

        /** Convenience factory for registering a {@code TypeNodeRecord} record with no request matcher. */
        public static TypeNodeRecord of(SchemaNodeRegistry registry) {
            return new TypeNodeRecord(null, registry, null);
        }
    }
}