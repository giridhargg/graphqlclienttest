package io.github.giridhargg.graphqlclienttest.httpgraphqlclient;

import org.jspecify.annotations.NonNull;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.support.DefaultGraphQlRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link ClientGraphQlRequest} implementation that checks for 'persistedQuery' attribute in the
 * extensions map and removes the 'query' attribute from request body to follow graphql specification.
 */
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

    /**
     * Removes 'query' attribute from the body
     */
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
