package io.github.giridhargg.graphqlclienttest.httpgraphqlclient;

import graphql.ExecutionInput;
import graphql.GraphQLException;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlTestExecutor;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link MockGraphQlServer} implementation backing
 * {@link HttpGraphQlClientTest @HttpGraphQlClientTest}: replaces the {@code ClientHttpConnector}
 * on a {@link WebClient.Builder} with one that executes every request entirely in-memory against
 * a {@link GraphQlTestExecutor}, never touching the network.
 *
 * <p>Construction is hidden behind {@link #createServer} — consumers never instantiate this class
 * directly, only the {@link MockGraphQlServer} supertype it returns.</p>
 */
class InMemoryMockGraphQlServer extends MockGraphQlServer {

    /**
     * Builds a {@link MockGraphQlServer} and mutates {@code webClientBuilder} in place, attaching
     * the in-memory connector. Callers must supply the <em>same</em> builder instance that will
     * later be used to build the {@link WebClient} consumed by {@code HttpGraphQlClient} — the
     * connector is registered via mutation, not by returning a new builder.
     */
    static MockGraphQlServer createServer(
            WebClient.Builder webClientBuilder, GraphQlTestExecutor graphQlTestExecutor, ObjectMapper objectMapper) {
        return new InMemoryMockGraphQlServer.InMemoryMockGraphQlServerBuilder(
                webClientBuilder,
                graphQlTestExecutor,
                objectMapper)
                .build();
    }

    /** Validated parameter holder for {@link #createServer}; not part of the public API surface. */
    record InMemoryMockGraphQlServerBuilder(WebClient.Builder webClientBuilder,
                                                    GraphQlTestExecutor graphQlTestExecutor,
                                                    ObjectMapper objectMapper) {
        InMemoryMockGraphQlServerBuilder {
            Assert.notNull(webClientBuilder, "RestClient.Builder must not be null");
            Assert.notNull(graphQlTestExecutor, "GraphQlTestExecutor must not be null");
            Assert.notNull(objectMapper, "ObjectMapper must not be null");
        }

        MockGraphQlServer build() {
                var server = new InMemoryMockGraphQlServer();
                var connector = server.new MockClientHttpConnector(graphQlTestExecutor, objectMapper);
                webClientBuilder.clientConnector(connector);
                return server;
            }
    }

    /**
     * Intercepts every outbound {@link WebClient} request, parses it as a GraphQL-over-HTTP
     * request body, executes it against the in-memory {@link GraphQlTestExecutor}, and returns
     * either a single JSON response ({@code query}/{@code mutation}) or a {@code text/event-stream}
     * response framed per the {@code graphql-sse} "distinct connections" protocol
     * ({@code subscription}).
     *
     * <h2>Supported request shapes</h2>
     * <ul>
     *   <li>Standard {@code {"query": "...", "variables": {...}, "operationName": "..."}} bodies</li>
     *   <li>Multi-operation documents, disambiguated via {@code operationName}</li>
     *   <li>Persisted Query requests
     *       ({@code extensions.persistedQuery.sha256Hash}), resolved against
     *       {@link GraphQlStaticTestAssets.Assets#persistedQueries()}</li>
     * </ul>
     */
    private class MockClientHttpConnector implements ClientHttpConnector {
        private static final String QUERY_KEY = "query";
        private static final String OPERATION_NAME_KEY = "operationName";
        private static final String VARIABLES_KEY = "variables";
        private static final String EXTENSIONS_KEY = "extensions";
        private static final String PERSISTED_QUERY_KEY = "persistedQuery";
        private static final String SHA256_HASH_KEY = "sha256Hash";

        private final GraphQlTestExecutor graphQlTestExecutor;
        private final GraphQlStaticTestAssets.Assets staticAssets;
        private final ObjectMapper objectMapper;

        MockClientHttpConnector(
                GraphQlTestExecutor graphQlTestExecutor, @Nullable ObjectMapper objectMapper) {
            this.graphQlTestExecutor = graphQlTestExecutor;
            this.staticAssets = graphQlTestExecutor.getGraphQlStaticAssets();
            this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        }

        /**
         * Captures the outbound request body, parses it, executes it in-memory, and produces a
         * synthetic {@link ClientHttpResponse} — for {@code query}/{@code mutation} operations,
         * the queued {@code MockGraphQlServer} expectation is advanced ({@link #pollExecutionRecord()})
         * as soon as the response is built; for {@code subscription} operations, advancing is
         * deferred until the event stream completes (see {@link #buildSseResponse}), since nested
         * field resolution against each emitted event happens after this method returns.
         */
        @Override
        public @NonNull Mono<ClientHttpResponse> connect(
                @NonNull HttpMethod method,
                @NonNull URI uri,
                Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

            var captureRequest = new MockClientHttpRequest(method, uri);
            var bodyBuffers = new AtomicReference<Flux<DataBuffer>>();
            captureRequest.setWriteHandler(dataBufferFlux -> {
                bodyBuffers.set(dataBufferFlux.cache());
                return Mono.empty();
            });

            return requestCallback.apply(captureRequest)
                    .then(Mono.defer(() -> collectBody(bodyBuffers.get())))
                    .flatMap(requestBody -> {
                        var parsed = ParsedGraphQlRequest.from(this, requestBody, objectMapper);
                        validateRequest(parsed);
                        var operationType = resolveOperationType(parsed.query(), parsed.operationName());
                        if (operationType == OperationDefinition.Operation.SUBSCRIPTION) {
                            return Mono.just(buildSseResponse(parsed));
                        } else {
                            var responseJson = objectMapper.writeValueAsString(
                                    graphQlTestExecutor.executeQuery(
                                            parsed.query(), parsed.variables(), parsed.operationName(), parsed.extensions()));
                            pollExecutionRecord();
                            return Mono.just(buildResponse(responseJson));
                        }
                    });
        }

        /**
         * Parses {@code query} as a GraphQL document and determines which operation
         * ({@code query}/{@code mutation}/{@code subscription}) applies, resolving by
         * {@code operationName} when the document defines more than one operation, per the
         * GraphQL spec's rule that {@code operationName} is mandatory in that case.
         *
         * @throws GraphQLException if the document has no operations, names an operation that
         *                          does not exist, or is ambiguous (multiple operations, no name given)
         */
        private OperationDefinition.Operation resolveOperationType(String query, @Nullable String operationName) {
            Document document = new Parser().parseDocument(query);

            var operationDefinitions = document.getDefinitions().stream()
                    .filter(OperationDefinition.class::isInstance)
                    .map(OperationDefinition.class::cast)
                    .toList();

            if (operationDefinitions.isEmpty()) {
                throw new GraphQLException("No operation definitions found in query document");
            }

            if (operationName != null && !operationName.isBlank()) {
                return operationDefinitions.stream()
                        .filter(op -> operationName.equals(op.getName()))
                        .findFirst()
                        .map(OperationDefinition::getOperation)
                        .orElseThrow(() -> new GraphQLException(
                                "No operation named '" + operationName + "' found in query document"));
            }

            // Per GraphQL spec: if operationName is not provided, the document must contain
            // exactly one operation.
            if (operationDefinitions.size() == 1) {
                return operationDefinitions.getFirst().getOperation();
            }

            throw new GraphQLException(
                    "operationName is required when the document contains multiple operations");
        }

        /**
         * Executes a subscription and frames each emitted event as a {@code graphql-sse}
         * "distinct connections" event ({@code event:next\ndata:<json>\n\n}), terminated by a
         * bare {@code event:complete\n\n} frame. The queued expectation is only advanced once
         * the stream completes or errors ({@code doFinally}), not when this method returns.
         */
        private ClientHttpResponse buildSseResponse(ParsedGraphQlRequest parsedGraphQlRequest) {
            Flux<Map<String, Object>> resultStream = graphQlTestExecutor.executeSubscription(
                    parsedGraphQlRequest.query,
                    parsedGraphQlRequest.variables,
                    parsedGraphQlRequest.operationName,
                    parsedGraphQlRequest.extensions);

            Flux<DataBuffer> sseBody = resultStream
                    .map(result -> {
                        try {
                            var json = objectMapper.writeValueAsString(result);
                            var frame = "event:next\ndata:" + json + "\n\n";
                            return (DataBuffer) DefaultDataBufferFactory.sharedInstance.wrap(
                                    frame.getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .concatWith(Mono.fromSupplier(() -> {
                        var completeFrame = "event:complete\n\n";  // no "data:" line
                        return (DataBuffer) DefaultDataBufferFactory.sharedInstance.wrap(
                                completeFrame.getBytes(StandardCharsets.UTF_8));
                    }))
                    .doFinally(signal -> pollExecutionRecord());

            var response = new MockClientHttpResponse(HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
            response.setBody(sseBody);
            return response;
        }

        private Mono<String> collectBody(Flux<DataBuffer> body) {
            if (body == null) {
                return Mono.error(new IllegalStateException("no request body was written"));
            }
            return body.map(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);
                return new String(bytes, StandardCharsets.UTF_8);
            }).collect(Collectors.joining());
        }

        /**
         * Runs the parsed request through {@link MockGraphQlServer#matchExecutionInput}, allowing
         * consumers who registered request-matching expectations (via {@code expect(...)}/
         * {@code expectExact(...)}) to assert on the actual outbound request shape.
         */
        void validateRequest(ParsedGraphQlRequest request) {
            var builder = ExecutionInput.newExecutionInput();
            if (request.query() != null) {
                builder.query(request.query());
            }
            if (request.operationName() != null) {
                builder.operationName(request.operationName());
            }
            if (request.variables() != null) {
                builder.variables(request.variables());
            }
            if (request.extensions() != null) {
                builder.extensions(request.extensions());
            }
            var executionInput = builder.build();
            matchExecutionInput(executionInput);
        }

        /**
         * Resolves the request's GraphQL document text: either directly from the {@code query}
         * field, or — if {@code extensions.persistedQuery.sha256Hash} is present — by looking up
         * the matching persisted query document.
         *
         * @throws GraphQLException if a persisted query hash is given but not found, or if
         *                          neither a {@code query} nor a resolvable persisted query hash
         *                          is present
         */
        private String resolveQuery(JsonNode node) {
            if (node.has(EXTENSIONS_KEY)) {
                var extensions = node.get(EXTENSIONS_KEY);
                var persisted = extensions.has(PERSISTED_QUERY_KEY)
                        ? extensions.get(PERSISTED_QUERY_KEY) : null;
                if (persisted != null && persisted.has(SHA256_HASH_KEY)) {
                    var hash = persisted.get(SHA256_HASH_KEY).asString();
                    // resolved from injected assets - no static access
                    var persistedQuery = staticAssets.persistedQueries().get(hash);
                    if (persistedQuery == null) {
                        throw new GraphQLException("Persisted query not found for hash: " + hash);
                    }
                    return persistedQuery;
                }
            }
            if (node.has(QUERY_KEY) && !node.get(QUERY_KEY).isNull()) {
                return node.get(QUERY_KEY).asString();
            }
            throw new GraphQLException("No query or persisted query has found in request");
        }

        /** Wraps a single JSON response body as a synthetic {@code 200 OK} HTTP response. */
        private ClientHttpResponse buildResponse(String responseJson) {
            var response = new MockClientHttpResponse(HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.setBody(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(
                    responseJson.getBytes(StandardCharsets.UTF_8))));
            return response;
        }

        /**
         * The fully-parsed shape of a GraphQL-over-HTTP request body, with persisted queries
         * already resolved to document text and the {@code persistedQuery} extension key
         * stripped (since it has already been consumed and forwarding it further is unnecessary).
         */
        private record ParsedGraphQlRequest(
                String query,
                @Nullable String operationName,
                @Nullable Map<String, Object> variables,
                @Nullable Map<String, Object> extensions) {
            static ParsedGraphQlRequest from(MockClientHttpConnector clientHttpConnector, String requestBody, ObjectMapper objectMapper) {
                var node = objectMapper.readTree(requestBody);
                var query = clientHttpConnector.resolveQuery(node);   // static helper
                var operationName = node.has(OPERATION_NAME_KEY) && !node.get(OPERATION_NAME_KEY).isNull()
                        ? node.get(OPERATION_NAME_KEY).asString() : null;
                Map<String, Object> variables = node.has(VARIABLES_KEY) && !node.get(VARIABLES_KEY).isNull()
                        ? objectMapper.convertValue(node.get(VARIABLES_KEY), Map.class) : null;
                Map<String, Object> rawExtensions = node.has(EXTENSIONS_KEY) && !node.get(EXTENSIONS_KEY).isNull()
                        ? new java.util.LinkedHashMap<>(objectMapper.convertValue(node.get(EXTENSIONS_KEY), Map.class)) : null;
                if (rawExtensions != null) rawExtensions.remove(PERSISTED_QUERY_KEY);
                var extensions = (rawExtensions == null || rawExtensions.isEmpty()) ? null : rawExtensions;

                return new ParsedGraphQlRequest(query, operationName, variables, extensions);
            }
        }
    }
}
