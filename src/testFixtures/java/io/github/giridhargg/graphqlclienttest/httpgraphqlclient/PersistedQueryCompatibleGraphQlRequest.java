package io.github.giridhargg.graphqlclienttest.httpgraphqlclient;

import org.jspecify.annotations.NonNull;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.support.DefaultGraphQlRequest;

import java.util.HashMap;
import java.util.Map;

public class PersistedQueryCompatibleGraphQlRequest
        extends DefaultGraphQlRequest
        implements ClientGraphQlRequest {

    private final ClientGraphQlRequest request;

    public PersistedQueryCompatibleGraphQlRequest(ClientGraphQlRequest request) {
        super(
                request.getDocument(),
                request.getOperationName(),
                request.getVariables(),
                request.getExtensions());
        this.request = request;
    }

    @Override
    public @NonNull Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>(super.toMap());
        if (this.getExtensions().containsKey("persistedQuery")) {
            map.remove("query");
        }
        return map;
    }

    @Override
    public @NonNull Map<String, Object> getAttributes() {
        return request.getAttributes();
    }
}
