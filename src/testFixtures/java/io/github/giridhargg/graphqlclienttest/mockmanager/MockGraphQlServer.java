package io.github.giridhargg.graphqlclienttest.mockmanager;

import graphql.ExecutionInput;
import graphql.schema.DataFetchingEnvironment;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode.QueryNode;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode.SchemaNodeRegistry;
import org.jspecify.annotations.Nullable;


/**
 * The primary consumer-facing API of this library: a FIFO queue of stubbed GraphQL responses that
 * consumer tests populate (via {@link #resolveFrom}), and that the in-memory or locally-running
 * GraphQL engine drains (via {@link #resolve}) as outbound requests are made.
 *
 * <p>One {@code MockGraphQlServer} bean exists per test context. It is injected automatically by
 * either {@code @HttpGraphQlClientTest} (in-memory, unit-test style) or
 * {@code @EnableGraphQlServerTestConfiguration} (real local server, integration-test style) — see
 * the project README for when to use which.</p>
 *
 * <h2>Resolution strategies</h2>
 * <p>Two ways to describe what a stubbed response should look like, selected by which overload of
 * {@link #resolveFrom} is called:</p>
 * <ul>
 *   <li>{@link #resolveFrom(QueryNode)} — a typed tree mirroring the response shape, including
 *       the root field. Supports partial success/error at any depth and within list elements.</li>
 *   <li>{@link #resolveFrom(SchemaNodeRegistry)} — per-{@code (type, field)} resolver
 *       functions with full access to {@code DataFetchingEnvironment} (arguments, parent source,
 *       context), mirroring how a real {@code @SchemaMapping} resolver behaves.</li>
 * </ul>
 *
 * <h2>Queueing multiple responses</h2>
 * <p>Each call to {@code resolveFrom(...)} appends one {@link ExecutionRecord} to an internal
 * FIFO queue; each completed outbound GraphQL request consumes exactly one record, in
 * registration order. This is what allows a single test method to stub multiple sequential
 * outbound requests (e.g. paginated fetches) by calling {@code resolveFrom(...)} more than once.</p>
 *
 * <h2>Per-test isolation</h2>
 * <p>The queue is reset automatically before each test method via
 * {@code MockGraphQlServerResetExtension} — consumers do not need {@code @DirtiesContext} or
 * manual {@link #reset()} calls between test methods within the same test class.</p>
 */
public class MockGraphQlServer {

    private final ResolvingManager resolvingManager;

    public MockGraphQlServer() {
        this.resolvingManager = new ResolvingManager();
    }

    /** Equivalent to {@code new MockGraphQlServer()}; used by the library's auto-configuration. */
    public static MockGraphQlServer createServer() {
        return new MockGraphQlServer();
    }

    /**
     * Advances the queue past its current head record. Called by the connector/interceptor
     * machinery once an outbound request has been fully resolved — not normally called directly
     * by consumer test code.
     */
    public void pollExecutionRecord() {
        this.resolvingManager.pollExecutionRecord();
    }

    /**
     * If the head of the queue carries a request-matching expectation (registered via
     * {@link #expect}/{@link #expectExact}), asserts that {@code executionInput} satisfies it.
     * Does nothing if the head record has no such expectation.
     */
    public void matchExecutionInput(ExecutionInput executionInput) {
        this.resolvingManager.matchExecutionInput(executionInput);
    }

    /**
     * Begins registering a response paired with a <em>partial</em> match expectation against the
     * next outbound request: fields left {@code null} on {@code executionInput} are not checked,
     * but every field that is set must match.
     */
    public ResolvingManager.ExecutionQueueBuilder expect(ExecutionInput executionInput) {
        return this.resolvingManager.expectInput(executionInput);
    }

    /**
     * Begins registering a response paired with an <em>exact</em> match expectation against the
     * next outbound request: every field of {@code executionInput} must match the actual request
     * exactly.
     */
    public ResolvingManager.ExecutionQueueBuilder expectExact(ExecutionInput executionInput) {
        return this.resolvingManager.expectExactInput(executionInput);
    }

    /**
     * Queues a response described by a typed {@link QueryNode} tree, with no request-matching
     * expectation. See {@link ExecutionRecord.GraphNodeRecord}.
     */
    public void resolveFrom(QueryNode queryNode) {
        this.resolvingManager.resolveFrom(queryNode);
    }

    /**
     * Queues a response described by per-field resolver functions, with no request-matching
     * expectation. See {@link ExecutionRecord.TypeNodeRecord}.
     */
    public void resolveFrom(SchemaNodeRegistry registry) {
        this.resolvingManager.resolveFrom(registry);
    }

    /**
     * Resolves a single field's value during GraphQL execution by delegating to the queue's
     * current head record. This is the method the unified data fetcher (wired by
     * {@code GraphQlTestExecutor} and {@code RuntimeWiringTestAutoConfiguration}) calls for
     * every field of every type in the schema — not normally called directly by consumer test
     * code.
     */
    public @Nullable Object resolve(DataFetchingEnvironment environment) throws Throwable {
        return this.resolvingManager.retrieveResponse(environment);
    }

    /** Clears the queue. Called automatically before each test method; rarely needed directly. */
    public void reset() {
        this.resolvingManager.reset();
    }
}
