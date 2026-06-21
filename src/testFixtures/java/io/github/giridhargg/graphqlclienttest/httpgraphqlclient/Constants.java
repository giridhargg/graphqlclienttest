package io.github.giridhargg.graphqlclienttest.httpgraphqlclient;

public class Constants {

    /** Bean name for the {@link org.springframework.web.reactive.function.client.WebClient.Builder} captured by the in-memory connector. */
    public static final String WEB_CLIENT_TEST_BUILDER = "webClientTestBuilder";

    /** Bean name for the built {@link org.springframework.web.reactive.function.client.WebClient} used by the {@code HttpGraphQlClient}. */
    public static final String WEB_TEST_CLIENT = "webTestClient";

    /** Bean name for the {@link org.springframework.graphql.client.HttpGraphQlClient} that consumer code under test should inject. */
    public static final String HTTP_GRAPHQL_TEST_CLIENT = "httpGraphQlTestClient";

    /** Bean name for the cached {@link io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets.Assets} (compiled schema, persisted queries). */
    public static final String GRAPHQL_STATIC_TEST_ASSETS = "graphQlStaticTestAssets";

    /** Bean name for the {@link io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlTestExecutor} that runs queries against the compiled in-memory schema. */
    public static final String GRAPHQL_TEST_EXECUTOR = "graphQlTestExecutor";

    /** Bean name for the {@link io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer} consumers inject to stub responses. */
    public static final String MOCK_GRAPHQL_SERVER = "mockGraphQlServer";

}
