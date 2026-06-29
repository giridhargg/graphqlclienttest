// package io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient;
//
// import graphql.ExecutionInput;
// import graphql.GraphQLException;
// import graphql.language.Document;
// import graphql.language.OperationDefinition;
// import graphql.parser.Parser;
// import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
// import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlTestExecutor;
// import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
// import org.jspecify.annotations.NonNull;
// import org.jspecify.annotations.Nullable;
// import org.springframework.http.HttpRequest;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.MediaType;
// import org.springframework.http.client.ClientHttpRequestExecution;
// import org.springframework.http.client.ClientHttpRequestInterceptor;
// import org.springframework.http.client.ClientHttpResponse;
// import org.springframework.mock.http.client.MockClientHttpResponse;
// import org.springframework.util.Assert;
// import org.springframework.web.client.RestClient;
// import tools.jackson.databind.JsonNode;
// import tools.jackson.databind.ObjectMapper;
//
// import java.io.IOException;
// import java.nio.charset.StandardCharsets;
// import java.util.LinkedHashMap;
// import java.util.Map;
// import java.util.Objects;
//
/// **
// * {@link MockGraphQlServer} implementation backing
// * {@link HttpSyncGraphQlClientTest @HttpSyncGraphQlClientTest}: registers a
// * {@link ClientHttpRequestInterceptor} on a {@link RestClient.Builder} that resolves every
// * outbound request entirely in-memory against a {@link GraphQlTestExecutor}, never touching the
// * network.
// *
// * <p>This is the blocking counterpart to
// * {@link io.github.giridhargg.graphqlclienttest.httpgraphqlclient.InMemoryMockGraphQlServer}, and
// * shares the same request-parsing and persisted-query resolution logic. The two differ only in
// * their transport integration point: the reactive client replaces the entire
// * {@code ClientHttpConnector} and must synthesize request-body capture via a written
// * {@code Mono<Void>} callback, whereas {@link RestClient}'s blocking
// * {@link ClientHttpRequestInterceptor} contract hands the request body over directly as a
// * {@code byte[]} and lets the interceptor short-circuit the chain by returning a response without
// * ever calling {@link ClientHttpRequestExecution#execute}.</p>
// *
// * <h2>Subscriptions are not supported</h2>
// * <p>{@code HttpSyncGraphQlClient} is built on {@code SyncGraphQlTransport}, a strictly
// * request-response contract with no streaming capability — its {@code RequestSpec} does not even
// * expose a {@code retrieveSubscription}/{@code executeSubscription} method, unlike the reactive
// * client. This is a limitation of the blocking transport itself, not of this library: there is no
// * way to serve a subscription over {@code HttpSyncGraphQlClient}, in-memory or otherwise. Use
// * {@code @HttpGraphQlClientTest} (reactive) if your tests need subscription support.</p>
// *
// * <p>Construction is hidden behind {@link #createServer} — consumers never instantiate this class
// * directly, only the {@link MockGraphQlServer} supertype it returns.</p>
// */
// class InMemorySyncMockGraphQlServer extends MockGraphQlServer {
//
// /**
// * Builds a {@link MockGraphQlServer} and mutates {@code restClientBuilder} in place,
// * registering the in-memory interceptor. Callers must supply the <em>same</em> builder
// * instance that will later be used to build the {@link RestClient} consumed by
// * {@code HttpSyncGraphQlClient} — the interceptor is registered via mutation, not by
// * returning a new builder.
// */
// static MockGraphQlServer createServer(
// RestClient.Builder restClientBuilder, GraphQlTestExecutor graphQlTestExecutor, ObjectMapper
// objectMapper) {
// return new InMemorySyncMockGraphQlServerBuilder(
// restClientBuilder,
// graphQlTestExecutor,
// objectMapper)
// .build();
// }
//
// /** Validated parameter holder for {@link #createServer}; not part of the public API surface. */
// record InMemorySyncMockGraphQlServerBuilder(RestClient.Builder restClientBuilder,
// GraphQlTestExecutor graphQlTestExecutor,
// ObjectMapper objectMapper) {
// InMemorySyncMockGraphQlServerBuilder {
// Assert.notNull(restClientBuilder, "RestClient.Builder must not be null");
// Assert.notNull(graphQlTestExecutor, "GraphQlTestExecutor must not be null");
// Assert.notNull(objectMapper, "ObjectMapper must not be null");
// }
//
// MockGraphQlServer build() {
// var server = new InMemorySyncMockGraphQlServer();
// var interceptor = server.new MockClientHttpRequestInterceptor(graphQlTestExecutor, objectMapper);
// restClientBuilder.requestInterceptor(interceptor);
// return server;
// }
// }
//
// /**
// * Intercepts every outbound {@link RestClient} request, parses it as a GraphQL-over-HTTP
// * request body, executes it synchronously against the in-memory {@link GraphQlTestExecutor},
// * and returns a single synthetic JSON {@link ClientHttpResponse} — short-circuiting the
// * interceptor chain entirely, so {@link ClientHttpRequestExecution#execute} is never called
// * and no real I/O occurs.
// *
// * <h2>Supported request shapes</h2>
// * <ul>
// * <li>Standard {@code {"query": "...", "variables": {...}, "operationName": "..."}} bodies</li>
// * <li>Multi-operation documents, disambiguated via {@code operationName}</li>
// * <li>Automatic Persisted Query requests
// * ({@code extensions.persistedQuery.sha256Hash}), resolved against
// * {@link GraphQlStaticTestAssets.Assets#persistedQueries()}</li>
// * </ul>
// *
// * <p>{@code subscription} operations are rejected with a {@link GraphQLException} at parse
// * time — see the class-level Javadoc on {@link InMemorySyncMockGraphQlServer} for why.</p>
// */
// private class MockClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
// private static final String QUERY_KEY = "query";
// private static final String OPERATION_NAME_KEY = "operationName";
// private static final String VARIABLES_KEY = "variables";
// private static final String EXTENSIONS_KEY = "extensions";
// private static final String PERSISTED_QUERY_KEY = "persistedQuery";
// private static final String SHA256_HASH_KEY = "sha256Hash";
//
// private final GraphQlTestExecutor graphQlTestExecutor;
// private final GraphQlStaticTestAssets.Assets staticAssets;
// private final ObjectMapper objectMapper;
//
// MockClientHttpRequestInterceptor(
// GraphQlTestExecutor graphQlTestExecutor, @Nullable ObjectMapper objectMapper) {
// this.graphQlTestExecutor = graphQlTestExecutor;
// this.staticAssets = graphQlTestExecutor.getGraphQlStaticAssets();
// this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
// }
//
// /**
// * Parses the request body, executes it in-memory, advances the queued
// * {@code MockGraphQlServer} expectation, and returns a synthetic {@code 200 OK} JSON
// * response — never delegating to {@code execution}, so no real HTTP call is made.
// */
// @Override
// public @NonNull ClientHttpResponse intercept(
// @NonNull HttpRequest request, byte @NonNull [] body, @NonNull ClientHttpRequestExecution
// execution)
// throws IOException {
//
// var requestBody = new String(body, StandardCharsets.UTF_8);
// var parsed = ParsedGraphQlRequest.from(this, requestBody, objectMapper, staticAssets);
// validateRequest(parsed);
// rejectSubscriptions(parsed.query(), parsed.operationName());
//
// var responseJson = objectMapper.writeValueAsString(
// graphQlTestExecutor.executeQuery(
// parsed.query(), parsed.variables(), parsed.operationName(), parsed.extensions()));
// pollExecutionRecord();
// return buildResponse(responseJson);
// }
//
// /**
// * Parses {@code query} as a GraphQL document and rejects it outright if the resolved
// * operation is a {@code subscription} — there is no synchronous transport this library
// * (or {@code HttpSyncGraphQlClient} itself) can serve a subscription event stream over.
// * Resolves by {@code operationName} when the document defines more than one operation,
// * per the GraphQL spec's rule that {@code operationName} is mandatory in that case.
// *
// * @throws GraphQLException if the document has no operations, names an operation that
// * does not exist, is ambiguous (multiple operations, no name
// * given), or resolves to a {@code subscription}
// */
// private void rejectSubscriptions(String query, @Nullable String operationName) {
// Document document = new Parser().parseDocument(query);
//
// var operationDefinitions = document.getDefinitions().stream()
// .filter(OperationDefinition.class::isInstance)
// .map(OperationDefinition.class::cast)
// .toList();
//
// if (operationDefinitions.isEmpty()) {
// throw new GraphQLException("No operation definitions found in query document");
// }
//
// OperationDefinition.Operation operation;
// if (operationName != null && !operationName.isBlank()) {
// operation = operationDefinitions.stream()
// .filter(op -> operationName.equals(op.getName()))
// .findFirst()
// .map(OperationDefinition::getOperation)
// .orElseThrow(() -> new GraphQLException(
// "No operation named '" + operationName + "' found in query document"));
// } else if (operationDefinitions.size() == 1) {
// // Per GraphQL spec: if operationName is not provided, the document must contain
// // exactly one operation.
// operation = operationDefinitions.getFirst().getOperation();
// } else {
// throw new GraphQLException(
// "operationName is required when the document contains multiple operations");
// }
//
// if (operation == OperationDefinition.Operation.SUBSCRIPTION) {
// throw new GraphQLException(
// "HttpSyncGraphQlClient cannot execute subscription operations — "
// + "SyncGraphQlTransport is request-response only. "
// + "Use @HttpGraphQlClientTest (reactive HttpGraphQlClient) for "
// + "subscription tests.");
// }
// }
//
// /**
// * Runs the parsed request through {@link MockGraphQlServer#matchExecutionInput}, allowing
// * consumers who registered request-matching expectations (via {@code expect(...)}/
// * {@code expectExact(...)}) to assert on the actual outbound request shape.
// */
// void validateRequest(ParsedGraphQlRequest request) {
// var builder = ExecutionInput.newExecutionInput();
// if (request.query() != null) {
// builder.query(request.query());
// }
// if (request.operationName() != null) {
// builder.operationName(request.operationName());
// }
// if (request.variables() != null) {
// builder.variables(request.variables());
// }
// if (request.extensions() != null) {
// builder.extensions(request.extensions());
// }
// var executionInput = builder.build();
// matchExecutionInput(executionInput);
// }
//
// /**
// * Resolves the request's GraphQL document text: either directly from the {@code query}
// * field, or — if {@code extensions.persistedQuery.sha256Hash} is present — by looking up
// * the matching persisted query document.
// *
// * @throws GraphQLException if a persisted query hash is given but not found, or if
// * neither a {@code query} nor a resolvable persisted query hash
// * is present
// */
// private String resolveQuery(JsonNode node) {
// if (node.has(EXTENSIONS_KEY)) {
// var extensions = node.get(EXTENSIONS_KEY);
// var persisted = extensions.has(PERSISTED_QUERY_KEY)
// ? extensions.get(PERSISTED_QUERY_KEY) : null;
// if (persisted != null && persisted.has(SHA256_HASH_KEY)) {
// var hash = persisted.get(SHA256_HASH_KEY).asString();
// // resolved from injected assets - no static access
// var persistedQuery = staticAssets.persistedQueries().get(hash);
// if (persistedQuery == null) {
// throw new GraphQLException("Persisted query not found for hash: " + hash);
// }
// return persistedQuery;
// }
// }
// if (node.has(QUERY_KEY) && !node.get(QUERY_KEY).isNull()) {
// return node.get(QUERY_KEY).asString();
// }
// throw new GraphQLException("No query or persisted query has found in request");
// }
//
// /** Wraps a single JSON response body as a synthetic {@code 200 OK} HTTP response. */
// private ClientHttpResponse buildResponse(String responseJson) {
// var response = new MockClientHttpResponse(
// responseJson.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
// response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
// return response;
// }
//
// /**
// * The fully-parsed shape of a GraphQL-over-HTTP request body, with persisted queries
// * already resolved to document text and the {@code persistedQuery} extension key
// * stripped (since it has already been consumed and forwarding it further is unnecessary).
// */
// private record ParsedGraphQlRequest(
// String query,
// @Nullable String operationName,
// @Nullable Map<String, Object> variables,
// @Nullable Map<String, Object> extensions) {
// static ParsedGraphQlRequest from(MockClientHttpRequestInterceptor interceptor, String
// requestBody, ObjectMapper objectMapper,
// GraphQlStaticTestAssets.Assets staticAssets) {
// var node = objectMapper.readTree(requestBody);
// var query = interceptor.resolveQuery(node);
// var operationName = node.has(OPERATION_NAME_KEY) && !node.get(OPERATION_NAME_KEY).isNull()
// ? node.get(OPERATION_NAME_KEY).asString() : null;
// Map<String, Object> variables = node.has(VARIABLES_KEY) && !node.get(VARIABLES_KEY).isNull()
// ? objectMapper.convertValue(node.get(VARIABLES_KEY), Map.class) : null;
// Map<String, Object> rawExtensions = node.has(EXTENSIONS_KEY) &&
// !node.get(EXTENSIONS_KEY).isNull()
// ? new LinkedHashMap<>(objectMapper.convertValue(node.get(EXTENSIONS_KEY), Map.class)) : null;
// if (rawExtensions != null) rawExtensions.remove(PERSISTED_QUERY_KEY);
// var extensions = (rawExtensions == null || rawExtensions.isEmpty()) ? null : rawExtensions;
//
// return new ParsedGraphQlRequest(query, operationName, variables, extensions);
// }
// }
// }
// }

package io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient;

import graphql.ExecutionInput;
import graphql.GraphQLException;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import io.github.giridhargg.graphqlclienttest.graphqlengine.ApqRegistry;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlTestExecutor;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link MockGraphQlServer} implementation backing {@link
 * HttpSyncGraphQlClientTest @HttpSyncGraphQlClientTest}: registers a {@link
 * ClientHttpRequestInterceptor} on a {@link RestClient.Builder} that resolves every outbound
 * request entirely in-memory against a {@link GraphQlTestExecutor}, never touching the network.
 *
 * <p>This is the blocking counterpart to {@link
 * io.github.giridhargg.graphqlclienttest.httpgraphqlclient.InMemoryMockGraphQlServer}, and shares
 * the same request-parsing and persisted-query resolution logic. The two differ only in their
 * transport integration point: the reactive client replaces the entire {@code ClientHttpConnector}
 * and must synthesize request-body capture via a written {@code Mono<Void>} callback, whereas
 * {@link RestClient}'s blocking {@link ClientHttpRequestInterceptor} contract hands the request
 * body over directly as a {@code byte[]} and lets the interceptor short-circuit the chain by
 * returning a response without ever calling {@link ClientHttpRequestExecution#execute}.
 *
 * <h2>Subscriptions are not supported</h2>
 *
 * <p>{@code HttpSyncGraphQlClient} is built on {@code SyncGraphQlTransport}, a strictly
 * request-response contract with no streaming capability — its {@code RequestSpec} does not even
 * expose a {@code retrieveSubscription}/{@code executeSubscription} method, unlike the reactive
 * client. This is a limitation of the blocking transport itself, not of this library: there is no
 * way to serve a subscription over {@code HttpSyncGraphQlClient}, in-memory or otherwise. Use
 * {@code @HttpGraphQlClientTest} (reactive) if your tests need subscription support.
 *
 * <p>Construction is hidden behind {@link #createServer} — consumers never instantiate this class
 * directly, only the {@link MockGraphQlServer} supertype it returns.
 */
class InMemorySyncMockGraphQlServer extends MockGraphQlServer {

  /**
   * Builds a {@link MockGraphQlServer} and mutates {@code restClientBuilder} in place, registering
   * the in-memory interceptor. Callers must supply the <em>same</em> builder instance that will
   * later be used to build the {@link RestClient} consumed by {@code HttpSyncGraphQlClient} — the
   * interceptor is registered via mutation, not by returning a new builder.
   */
  static MockGraphQlServer createServer(
      RestClient.Builder restClientBuilder,
      GraphQlTestExecutor graphQlTestExecutor,
      ApqRegistry apqRegistry,
      ObjectMapper objectMapper) {
    return new InMemorySyncMockGraphQlServer.InMemorySyncMockGraphQlServerBuilder(
            restClientBuilder, graphQlTestExecutor, apqRegistry, objectMapper)
        .build();
  }

  record InMemorySyncMockGraphQlServerBuilder(
      RestClient.Builder restClientBuilder,
      GraphQlTestExecutor graphQlTestExecutor,
      ApqRegistry apqRegistry,
      ObjectMapper objectMapper) {
    InMemorySyncMockGraphQlServerBuilder {
      Assert.notNull(restClientBuilder, "RestClient.Builder must not be null");
      Assert.notNull(graphQlTestExecutor, "GraphQlTestExecutor must not be null");
      Assert.notNull(apqRegistry, "ApqRegistry must not be null");
      Assert.notNull(objectMapper, "ObjectMapper must not be null");
    }

    MockGraphQlServer build() {
      var server = new InMemorySyncMockGraphQlServer();
      var interceptor =
          server
          .new MockClientHttpRequestInterceptor(graphQlTestExecutor, apqRegistry, objectMapper);
      restClientBuilder.requestInterceptor(interceptor);
      return server;
    }
  }

  /**
   * Intercepts every outbound {@link RestClient} request, parses it as a GraphQL-over-HTTP request
   * body, executes it synchronously against the in-memory {@link GraphQlTestExecutor}, and returns
   * a single synthetic JSON {@link ClientHttpResponse} — short-circuiting the interceptor chain
   * entirely, so {@link ClientHttpRequestExecution#execute} is never called and no real I/O occurs.
   *
   * <h2>Supported request shapes</h2>
   *
   * <ul>
   *   <li>Standard {@code {"query": "...", "variables": {...}, "operationName": "..."}} bodies
   *   <li>Multi-operation documents, disambiguated via {@code operationName}
   *   <li>Automatic Persisted Query requests ({@code extensions.persistedQuery.sha256Hash}),
   *       resolved against {@link GraphQlStaticTestAssets.Assets#persistedQueries()}
   * </ul>
   *
   * <p>{@code subscription} operations are rejected with a {@link GraphQLException} at parse time —
   * see the class-level Javadoc on {@link InMemorySyncMockGraphQlServer} for why.
   */
  private class MockClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
    private static final String QUERY_KEY = "query";
    private static final String OPERATION_NAME_KEY = "operationName";
    private static final String VARIABLES_KEY = "variables";
    private static final String EXTENSIONS_KEY = "extensions";
    private static final String PERSISTED_QUERY_KEY = "persistedQuery";
    private static final String SHA256_HASH_KEY = "sha256Hash";

    private final GraphQlTestExecutor graphQlTestExecutor;
    private final GraphQlStaticTestAssets.Assets staticAssets;
    private final ApqRegistry apqRegistry;
    private final ObjectMapper objectMapper;

    MockClientHttpRequestInterceptor(
        GraphQlTestExecutor graphQlTestExecutor,
        ApqRegistry apqRegistry,
        @Nullable ObjectMapper objectMapper) {
      this.graphQlTestExecutor = graphQlTestExecutor;
      this.staticAssets = graphQlTestExecutor.getGraphQlStaticAssets();
      this.apqRegistry = apqRegistry;
      this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    /**
     * Parses the request body via the APQ protocol, executes the resolved query in-memory, advances
     * the queued {@code MockGraphQlServer} expectation, and returns a synthetic {@code 200 OK} JSON
     * response — never delegating to {@code execution}, so no real HTTP call is made.
     *
     * <p>APQ phase-1 misses and hash-mismatch errors are returned as valid GraphQL JSON error
     * responses (not thrown) so the client receives them as proper protocol-level errors.
     */
    @Override
    public @NonNull ClientHttpResponse intercept(
        @NonNull HttpRequest request,
        byte @NonNull [] body,
        @NonNull ClientHttpRequestExecution execution)
        throws IOException {

      var requestBody = new String(body, StandardCharsets.UTF_8);
      var node = objectMapper.readTree(requestBody);
      var resolution = resolveQuery(node);

      // APQ error outcomes - return in-band without executing the query
      if (resolution instanceof ApqResolution.NotFound(var hash)) {
        return buildResponse(
            objectMapper.writeValueAsString(
                Map.of(
                    "errors",
                    java.util.List.of(
                        Map.of(
                            "message",
                            "PersistedQueryNotFound",
                            "extensions",
                            Map.of(
                                "code", "PERSISTED_QUERY_NOT_FOUND", "persistedQueryId", hash))))));
      }
      if (resolution instanceof ApqResolution.HashMismatch(var claimed, var actual)) {
        return buildResponse(
            objectMapper.writeValueAsString(
                Map.of(
                    "errors",
                    java.util.List.of(
                        Map.of(
                            "message",
                            "PersistedQueryHashMismatch",
                            "extensions",
                            Map.of(
                                "code",
                                "PERSISTED_QUERY_HASH_MISMATCH",
                                "claimedHash",
                                claimed,
                                "actualHash",
                                actual))))));
      }

      var query = ((ApqResolution.Resolved) resolution).query();
      var parsed = ParsedGraphQlRequest.from(node, query, objectMapper);
      validateRequest(parsed);
      rejectSubscriptions(parsed.query(), parsed.operationName());

      var responseJson =
          objectMapper.writeValueAsString(
              graphQlTestExecutor.executeQuery(
                  parsed.query(), parsed.variables(), parsed.operationName(), parsed.extensions()));
      pollExecutionRecord();
      return buildResponse(responseJson);
    }

    /**
     * Resolves the request's GraphQL document via the full APQ two-phase protocol. See {@link
     * InMemorySyncMockGraphQlServer} class-level doc for the protocol description.
     */
    private ApqResolution resolveQuery(JsonNode node) {
      boolean hasPersistedExt =
          node.has(EXTENSIONS_KEY)
              && node.get(EXTENSIONS_KEY).has(PERSISTED_QUERY_KEY)
              && node.get(EXTENSIONS_KEY).get(PERSISTED_QUERY_KEY).has(SHA256_HASH_KEY);

      if (!hasPersistedExt) {
        if (node.has(QUERY_KEY) && !node.get(QUERY_KEY).isNull()) {
          return new ApqResolution.Resolved(node.get(QUERY_KEY).asString());
        }
        throw new GraphQLException("No query or persisted query found in request");
      }

      var hash = node.get(EXTENSIONS_KEY).get(PERSISTED_QUERY_KEY).get(SHA256_HASH_KEY).asString();
      var hasQuery =
          node.has(QUERY_KEY)
              && !node.get(QUERY_KEY).isNull()
              && !node.get(QUERY_KEY).asString().isBlank();

      if (!hasQuery) {
        // Phase 1: hash-only lookup
        return switch (apqRegistry.lookup(hash, staticAssets)) {
          case ApqRegistry.LookupResult.Found(var q) -> new ApqResolution.Resolved(q);
          case ApqRegistry.LookupResult.NotFound(var h) -> new ApqResolution.NotFound(h);
        };
      } else {

        // Phase 2: hash + query registration
        var query = node.get(QUERY_KEY).asString();
        return switch (apqRegistry.register(hash, query)) {
          case ApqRegistry.RegistrationResult.Registered ignored ->
              new ApqResolution.Resolved(query);
          case ApqRegistry.RegistrationResult.HashMismatch(var claimed, var actual) ->
              new ApqResolution.HashMismatch(claimed, actual);
        };
      }
    }

    private sealed interface ApqResolution {
      record Resolved(String query) implements ApqResolution {}

      record NotFound(String hash) implements ApqResolution {}

      record HashMismatch(String claimedHash, String actualHash) implements ApqResolution {}
    }

    /**
     * Parses {@code query} as a GraphQL document and rejects it outright if the resolved operation
     * is a {@code subscription} — there is no synchronous transport this library (or {@code
     * HttpSyncGraphQlClient} itself) can serve a subscription event stream over. Resolves by {@code
     * operationName} when the document defines more than one operation, per the GraphQL spec's rule
     * that {@code operationName} is mandatory in that case.
     *
     * @throws GraphQLException if the document has no operations, names an operation that does not
     *     exist, is ambiguous (multiple operations, no name given), or resolves to a {@code
     *     subscription}
     */
    private void rejectSubscriptions(String query, @Nullable String operationName) {
      Document document = new Parser().parseDocument(query);

      var operationDefinitions =
          document.getDefinitions().stream()
              .filter(OperationDefinition.class::isInstance)
              .map(OperationDefinition.class::cast)
              .toList();

      if (operationDefinitions.isEmpty()) {
        throw new GraphQLException("No operation definitions found in query document");
      }

      OperationDefinition.Operation operation;
      if (operationName != null && !operationName.isBlank()) {
        operation =
            operationDefinitions.stream()
                .filter(op -> operationName.equals(op.getName()))
                .findFirst()
                .map(OperationDefinition::getOperation)
                .orElseThrow(
                    () ->
                        new GraphQLException(
                            "No operation named '" + operationName + "' found in query document"));
      } else if (operationDefinitions.size() == 1) {
        // Per GraphQL spec: if operationName is not provided, the document must contain
        // exactly one operation.
        operation = operationDefinitions.getFirst().getOperation();
      } else {
        throw new GraphQLException(
            "operationName is required when the document contains multiple operations");
      }

      if (operation == OperationDefinition.Operation.SUBSCRIPTION) {
        throw new GraphQLException(
            "HttpSyncGraphQlClient cannot execute subscription operations — "
                + "SyncGraphQlTransport is request-response only. "
                + "Use @HttpGraphQlClientTest (reactive HttpGraphQlClient) for "
                + "subscription tests.");
      }
    }

    /**
     * Runs the parsed request through {@link MockGraphQlServer#matchExecutionInput}, allowing
     * consumers who registered request-matching expectations (via {@code expect(...)}/ {@code
     * expectExact(...)}) to assert on the actual outbound request shape.
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

    /** Wraps a single JSON response body as a synthetic {@code 200 OK} HTTP response. */
    private ClientHttpResponse buildResponse(String responseJson) {
      var response =
          new MockClientHttpResponse(responseJson.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
      response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      return response;
    }

    private record ParsedGraphQlRequest(
        String query,
        @Nullable String operationName,
        @Nullable Map<String, Object> variables,
        @Nullable Map<String, Object> extensions) {

      static ParsedGraphQlRequest from(
          JsonNode node, String resolvedQuery, ObjectMapper objectMapper) {
        var operationName =
            node.has(OPERATION_NAME_KEY) && !node.get(OPERATION_NAME_KEY).isNull()
                ? node.get(OPERATION_NAME_KEY).asString()
                : null;
        Map<String, Object> variables =
            node.has(VARIABLES_KEY) && !node.get(VARIABLES_KEY).isNull()
                ? objectMapper.convertValue(node.get(VARIABLES_KEY), Map.class)
                : null;
        Map<String, Object> rawExtensions =
            node.has(EXTENSIONS_KEY) && !node.get(EXTENSIONS_KEY).isNull()
                ? new LinkedHashMap<>(
                    objectMapper.convertValue(node.get(EXTENSIONS_KEY), Map.class))
                : null;
        if (rawExtensions != null) rawExtensions.remove(PERSISTED_QUERY_KEY);
        var extensions = (rawExtensions == null || rawExtensions.isEmpty()) ? null : rawExtensions;
        return new ParsedGraphQlRequest(resolvedQuery, operationName, variables, extensions);
      }
    }
  }
}
