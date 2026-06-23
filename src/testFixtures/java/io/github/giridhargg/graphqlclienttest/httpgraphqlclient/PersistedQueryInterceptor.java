package io.github.giridhargg.graphqlclienttest.httpgraphqlclient;

import org.jspecify.annotations.NonNull;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClientInterceptor;
import reactor.core.publisher.Mono;

/**
 * {@link GraphQlClientInterceptor} that wraps the {@link ClientGraphQlRequest} with
 * {@link PersistedQueryCompatibleGraphQlRequest}
 */
public class PersistedQueryInterceptor implements GraphQlClientInterceptor {

    public @NonNull Mono<ClientGraphQlResponse> intercept(
            @NonNull ClientGraphQlRequest request, Chain chain) {
        return chain.next(new PersistedQueryCompatibleGraphQlRequest(request));
    }
}
