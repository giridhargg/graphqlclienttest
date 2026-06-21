package io.github.giridhargg.graphqlclienttest.graphqlserverconfig;

import graphql.GraphQLException;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssetsProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;


/**
 * Resolves Persisted Query (PQ) requests against the persisted query documents loaded
 * by {@link GraphQlStaticTestAssets}, before they reach the GraphQL execution engine.
 *
 * <p>When an incoming request's {@code extensions.persistedQuery.sha256Hash} is present, this
 * interceptor looks up the corresponding document text and rewrites the request's {@code query}
 * field with it before continuing the chain — the rest of the pipeline (validation, execution,
 * the unified mock data fetcher) never needs to know persisted queries were involved at all.</p>
 *
 * <p>If no persisted-query extension is present, the request passes through unchanged. If the
 * hash does not match any loaded persisted query, the chain is short-circuited with a
 * {@link GraphQLException}.</p>
 *
 * <p>This is an opt-in feature: if no persisted query assets are configured (or none are found
 * on the classpath), {@code persistedQueries} is simply empty and every request passes through
 * unchanged, since none of them carry the persisted-query extension.</p>
 *
 * @see GraphQlStaticTestAssets
 */
class PersistedQueryTestInterceptor implements WebGraphQlInterceptor {

    private static final String SHA256_HASH = "sha256Hash";

    private final Map<String, String> persistedQueries;

    /**
     * @param properties resolves which persisted-queryNode-hashes file(s) and {@code .gql} document
     *                   pattern to load; the actual loading is delegated to
     *                   {@link GraphQlStaticTestAssets#forPaths}, which caches the result for the
     *                   lifetime of the JVM, so repeated construction across test classes with the
     *                   same configuration does not re-read the classpath.
     */
    PersistedQueryTestInterceptor(GraphQlStaticTestAssetsProperties properties) {
        // even though this interceptor is being invoked for multiple requests, the persisted queries are static assets
        // that are loaded once at first test startup time and cached in memory for fast lookup on subsequent requests
        this.persistedQueries = GraphQlStaticTestAssets.forPaths(properties).persistedQueries();
    }

    @Override
    public @NonNull Mono<WebGraphQlResponse> intercept(@NonNull WebGraphQlRequest request, @NonNull Chain chain) {
        Map<String, Object> extensions = request.getExtensions();
        Map<String, Object> persisted = extensions != null
                ? (Map<String, Object>) extensions.get("persistedQuery") : null;

        if (persisted != null && persisted.get(SHA256_HASH) != null) {
            return handlePersistedQuery(request, chain, persisted);
        }
        return chain.next(request);
    }

    /**
     * Rebuilds the request with its {@code queryNode} field replaced by the resolved persisted queryNode
     * document, then continues the chain.
     */
    private Mono<WebGraphQlResponse> handlePersistedQuery(
            WebGraphQlRequest request, Chain chain, Map<String, Object> persisted) {
        var hash = (String) persisted.get(SHA256_HASH);
        var persistedQuery = persistedQueries.get(hash);
        if (persistedQuery == null) {
            return Mono.error(new GraphQLException("Persisted queryNode not found for hash: " + hash));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("queryNode", persistedQuery);
        body.put("operationName", request.getOperationName());
        body.put("variables", request.getVariables());
        body.put("extensions", request.getExtensions());
        var newRequest = new WebGraphQlRequest(
                request.getUri().toUri(), request.getHeaders(), request.getCookies(),
                request.getRemoteAddress(), request.getAttributes(),
                body, request.getId(), request.getLocale());
        return chain.next(newRequest);
    }
}
