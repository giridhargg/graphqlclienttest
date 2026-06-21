package io.github.giridhargg.graphqlclienttest.mockmanager;

import graphql.ExecutionInput;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.execution.ResultPath;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.PropertyDataFetcher;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode.QueryNode;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode.SchemaNodeRegistry;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Owns the FIFO queue of {@link ExecutionRecord}s and dispatches every field resolution during
 * GraphQL execution to the strategy-specific resolution method for whichever record is currently
 * at the head of the queue.
 *
 * <p>Not part of the public API — consumers interact with {@link MockGraphQlServer} only.</p>
 *
 * <h2>Why dispatch lives here, not in the data fetcher itself</h2>
 * <p>A single unified {@code DataFetcher} is registered for every field of every type in the
 * compiled schema (see {@code GraphQlTestExecutor}/{@code RuntimeWiringTestAutoConfiguration}).
 * That fetcher always calls {@link #retrieveResponse}, which peeks the queue head and switches on
 * its concrete {@link ExecutionRecord} type — so each resolution strategy
 * ({@link #resolveDeepPathBased}, {@link #resolvePerFieldResolverBased}) only needs to know how to
 * resolve <em>its own</em> kind of stub, without needing to know which strategy is "active" ahead
 * of time.</p>
 */
class ResolvingManager {

    protected final Queue<ExecutionRecord> inputs = new ArrayDeque<>();

    /**
     * Begins registering a response paired with a partial-match request expectation. See
     * {@link MockGraphQlServer#expect}.
     */
    ResolvingManager.ExecutionQueueBuilder expectInput(ExecutionInput executionInput) {
        return new ResolvingManager.ExecutionQueueBuilder(executionInput, ExecutionRecord.MatchType.PARTIAL);
    }

    /**
     * Begins registering a response paired with an exact-match request expectation. See
     * {@link MockGraphQlServer#expectExact}.
     */
    ResolvingManager.ExecutionQueueBuilder expectExactInput(ExecutionInput executionInput) {
        return new ResolvingManager.ExecutionQueueBuilder(executionInput, ExecutionRecord.MatchType.EXACT);
    }

    /** Queues a {@link ExecutionRecord.GraphNodeRecord} record with no request-matching expectation. */
    void resolveFrom(QueryNode node) {
        this.inputs.add(ExecutionRecord.GraphNodeRecord.of(node));
    }

    /** Queues a {@link ExecutionRecord.TypeNodeRecord} record with no request-matching expectation. */
    void resolveFrom(SchemaNodeRegistry registry) {
        this.inputs.add(ExecutionRecord.TypeNodeRecord.of(registry));
    }

    /**
     * If the queue head carries a request-matching expectation, asserts {@code actualInput}
     * satisfies it (via AssertJ, so a mismatch fails the current test with a descriptive diff).
     * A no-op if the queue is empty or the head record has no expectation.
     */
    void matchExecutionInput(ExecutionInput actualInput) {
        var executionRecord = inputs.peek();
        if (executionRecord == null) return;

        var expectedInput = executionRecord.executionInput();
        if (expectedInput == null) return;
        if (Objects.equals(executionRecord.matchType(), ExecutionRecord.MatchType.EXACT)) {
            matchExact(expectedInput, actualInput);
        } else {
            match(expectedInput, actualInput);
        }
    }

    /** Partial match: every non-null field of {@code expectedInput} must equal {@code actualInput}'s. */
    void match(ExecutionInput expectedInput, ExecutionInput actualInput) {
        // expected and actual object's target positions are switched here for lenient comparison
        // which is, actual object can contain any kind of fields and values, but it should contain
        // all the fields and values that are present in expected.
        assertThat(expectedInput)
                .usingRecursiveComparison()
                .ignoringExpectedNullFields()
                .isEqualTo(actualInput);
    }

    /** Exact match: every field of {@code expectedInput} must equal {@code actualInput}'s. */
    private void matchExact(ExecutionInput expectedInput, ExecutionInput actualInput) {
        assertThat(actualInput)
                .usingRecursiveComparison()
                .isEqualTo(expectedInput);
    }


    /**
     * Single entry point invoked by the unified data fetcher for every field, every time. Peeks
     * (does not consume) the queue head and dispatches to the resolution method matching its
     * concrete {@link ExecutionRecord} type. Returns {@code null} if the queue is empty — this
     * is the expected behavior when an outbound request has no queued stub at all, rather than
     * an error condition.
     */
    @Nullable Object retrieveResponse(DataFetchingEnvironment environment) throws Throwable {
        var record = inputs.peek();
        if (record == null) return null;

        return switch (record) {
            case ExecutionRecord.GraphNodeRecord r -> resolveDeepPathBased(environment, r);
            case ExecutionRecord.TypeNodeRecord r -> resolvePerFieldResolverBased(environment, r);
        };
    }

    /**
     * Resolves a field against a {@link ExecutionRecord.GraphNodeRecord} record.
     *
     * <p>Uses O(1) "source chaining" rather than re-navigating the tree from its root on every
     * field: once a parent field resolves to a {@link QueryNode} (an {@code ObjectNode} or its
     * unwrapped {@code ListNode} elements), that node is returned as-is and becomes
     * {@code environment.getSource()} for the next level of fields — so each field only ever
     * needs a single child lookup from its immediate parent, not a full-depth walk. This keeps
     * per-field resolution cost proportional to the schema's branching factor, not its depth.</p>
     *
     * <p>The very first call for a given record (the root field) is the one exception: there is
     * no parent {@code QueryNode} source yet, so the root field name is read directly from
     * {@code environment.getExecutionStepInfo().getPath()} and looked up against the record's
     * top-level {@link QueryNode.ObjectNode}.</p>
     *
     * <p>If the looked-up node is a {@code Flux} (a subscription's stream of source events), it is
     * returned to {@code graphql-java} unchanged — the engine treats a {@code Publisher} returned
     * from the root subscription field's data fetcher as the event source, re-running the
     * selection set once per emitted item, each of which re-enters this same method with that
     * item as the new {@code QueryNode} source.</p>
     *
     * <p>A field with no corresponding node in the tree (i.e. not explicitly stubbed) falls back
     * to default property access against whatever the parent resolved to, so partially-stubbed
     * trees still resolve consistently for the fields the consumer did not bother stubbing.</p>
     */
    private @Nullable Object resolveDeepPathBased(DataFetchingEnvironment environment, ExecutionRecord.GraphNodeRecord record) throws Throwable {

        var source = environment.getSource();
        Object localNode;

        if (source instanceof QueryNode parentNode) {
            // O(1) lookup - single field/index step from parent, no root re-walk
            var fieldName = environment.getField().getName();
            localNode = (parentNode instanceof QueryNode.ObjectNode obj) ? obj.get(fieldName) : null;
        } else {
            // First call (root field) - full navigation from the stubbed root map
            var allSegments = environment.getExecutionStepInfo().getPath().toList();
            if (allSegments.isEmpty()) return null;
            var rootFieldName = (String) allSegments.get(0);
            localNode = record.queryNode() instanceof QueryNode.ObjectNode objectNode ?
                    objectNode.get(rootFieldName) : null; // now ObjectNode - compiles fine
        }

        if (localNode == null) {
            return PropertyDataFetcher.fetching(fieldKey(environment)).get(environment);
        }

        return switch (localNode) {
            case Flux<?> flux -> flux; // subscription root - graphql-java treats this as the event Publisher
            case QueryNode.LeafNode leafNode -> resolveLeafValue(leafNode.value(), environment);
            case QueryNode.ObjectNode obj -> obj;
            case QueryNode.ListNode listNode -> listNode.elements();
            default -> localNode; // plain scalar fallback, if ever stored directly
        };
    }

    /**
     * Resolves a field against a {@link ExecutionRecord.TypeNodeRecord} record by looking
     * up a resolver registered for the field's exact {@code (parent type name, field name)}
     * coordinate. The lookup is by schema field name, not alias, since resolver registration is
     * type/field-scoped and should apply uniformly regardless of how a particular query aliases
     * the field. A field with no registered resolver falls back to default property access
     * against the parent's resolved value, exactly as {@link #resolveDeepPathBased} does.
     */
    private @Nullable Object resolvePerFieldResolverBased(
            DataFetchingEnvironment environment, ExecutionRecord.TypeNodeRecord record) throws Throwable {

        var typeName = parentTypeName(environment);
        var fieldName = environment.getField().getName(); // schema field name, NOT alias - resolver registration is type/field-based
        var registry = record.registry();

        var resolver = registry.lookup(typeName, fieldName);
        if (resolver == null) {
            // No resolver registered for this (type, field) - fall back to default property access
            // against whatever the parent resolver returned as "source"
            return PropertyDataFetcher.fetching(fieldKey(environment)).get(environment);
        }

        var result = resolver.resolve(environment);
        return resolveLeafValue(result, environment);
    }

    /** The current field's parent object type name, used as half of a {@code FieldCoordinate} lookup key. */
    private String parentTypeName(DataFetchingEnvironment environment) {
        var parentType = environment.getParentType();
        if (parentType instanceof graphql.schema.GraphQLObjectType objType) {
            return objType.getName();
        }
        // Could be GraphQLInterfaceType etc. - use unwrapped type name as fallback
        return graphql.schema.GraphQLTypeUtil.simplePrint(parentType);
    }

    /**
     * Interprets a leaf-level stub value: a {@link Throwable} is thrown directly (graphql-java
     * converts it into an {@code INTERNAL_ERROR} unless it implements {@link GraphQLError}
     * itself), a {@link GraphQLError} is enriched ({@link #enrichError}) and wrapped as a
     * {@link DataFetcherResult} so its path/location are correctly attached, a pre-built
     * {@link DataFetcherResult} is passed straight through, and anything else is returned as
     * plain field data.
     */
    private @Nullable Object resolveLeafValue(@Nullable Object value, DataFetchingEnvironment environment) throws Throwable {
        if (value instanceof Throwable throwable) {
            throw throwable;
        }
        if (value instanceof GraphQLError graphQLError) {
            var enriched = enrichError(graphQLError, environment);
            return DataFetcherResult.newResult().error(enriched).data(null).build();
        }
        if (value instanceof DataFetcherResult<?> dataFetcherResult) {
            return dataFetcherResult;
        }
        return value;
    }

    /**
     * Fills in a stubbed {@link GraphQLError}'s {@code path} and {@code location} from the live
     * {@link DataFetchingEnvironment} when the consumer did not explicitly set them — this is
     * what lets error factories like {@code GraphQlErrors}/{@code SpringGraphQlErrors} be called
     * with no path argument at all and still produce a correctly-pathed error pointing at the
     * exact field (and list index, if applicable) being resolved when the error fires, including
     * for errors nested several levels deep or repeated across different list elements.
     *
     * <p>If the consumer's original error already explicitly set a path or location, those are
     * preserved rather than overwritten — auto-enrichment only fills gaps.</p>
     */
    private GraphQLError enrichError(GraphQLError original, DataFetchingEnvironment environment) {
        var builder = GraphqlErrorBuilder.newError(environment); // seeds path + location from environment!

        builder.message(original.getMessage());
        if (original.getErrorType() != null) {
            builder.errorType(original.getErrorType());
        }
        if (original.getExtensions() != null && !original.getExtensions().isEmpty()) {
            builder.extensions(original.getExtensions());
        }
        // If the original already explicitly set a path/location, prefer those:
        if (original.getPath() != null && !original.getPath().isEmpty()) {
            builder.path(ResultPath.fromList(original.getPath()));
        }
        if (original.getLocations() != null && !original.getLocations().isEmpty()) {
            builder.location(original.getLocations().getFirst());
        }

        return builder.build();
    }

    /** The response key for a field: its alias if present, otherwise its schema field name. */
    private String fieldKey(DataFetchingEnvironment environment) {
        var field = environment.getField();
        return field.getAlias() != null ? field.getAlias() : field.getName();
    }

    /** Discards the queue head. Called once a request has been fully resolved. */
    void pollExecutionRecord() {
        inputs.poll();
    }

    /** Clears the entire queue, discarding any unconsumed stubs. */
    void reset() {
        inputs.clear();
    }

    /**
     * Fluent continuation returned by {@link #expectInput}/{@link #expectExactInput}, pairing a
     * request-matching expectation with whichever resolution strategy the consumer chooses next.
     */
    public class ExecutionQueueBuilder {

        private final ExecutionRecord.MatchType matchType;

        private final ExecutionInput executionInput;

        public ExecutionQueueBuilder(
                ExecutionInput executionInput,
                ExecutionRecord.MatchType matchType) {
            this.matchType = matchType;
            this.executionInput = executionInput;
        }

        /** Completes the expectation with a {@link QueryNode}-based response. */
        public void andResolveFrom(QueryNode queryNode) {
            inputs.add(new ExecutionRecord.GraphNodeRecord(executionInput, queryNode, matchType));
        }

        /** Completes the expectation with a per-field-resolver-based response. */
        public void andResolveFrom(SchemaNodeRegistry registry) {
            inputs.add(new ExecutionRecord.TypeNodeRecord(executionInput, registry, matchType));
        }
    }
}
