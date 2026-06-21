package io.github.giridhargg.graphqlclienttest.httpgraphqlclient;

import org.jspecify.annotations.NonNull;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClientInterceptor;
import reactor.core.publisher.Mono;

public class PersistedQueryInterceptor implements GraphQlClientInterceptor {
    public @NonNull Mono<ClientGraphQlResponse> intercept(
            @NonNull ClientGraphQlRequest request, Chain chain) {
        var pqRequest = new PersistedQueryCompatibleGraphQlRequest(request);
        return chain.next(pqRequest);
    }
}
