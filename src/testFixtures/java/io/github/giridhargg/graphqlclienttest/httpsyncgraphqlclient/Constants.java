package io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient;

import io.github.giridhargg.graphqlclienttest.graphqlengine.ApqRegistry;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlTestExecutor;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.web.client.RestClient;

public class Constants {

  /** Bean name for the {@link RestClient.Builder} captured by the in-memory interceptor. */
  public static final String REST_CLIENT_TEST_BUILDER = "restClientTestBuilder";

  /** Bean name for the built {@link RestClient} used by the {@code HttpSyncGraphQlClient}. */
  public static final String REST_TEST_CLIENT = "restTestClient";

  /**
   * Bean name for the {@link HttpSyncGraphQlClient} that consumer code under test should inject.
   */
  public static final String HTTP_SYNC_GRAPHQL_TEST_CLIENT = "httpSyncGraphQlTestClient";

  /**
   * Bean name for the cached {@link GraphQlStaticTestAssets.Assets} (compiled schema, persisted
   * queries).
   */
  public static final String GRAPHQL_STATIC_TEST_ASSETS = "graphQlStaticTestAssets";

  /**
   * Bean name for the {@link GraphQlTestExecutor} that runs queries against the compiled in-memory
   * schema.
   */
  public static final String GRAPHQL_TEST_EXECUTOR = "graphQlTestExecutor";

  /** Bean name for the {@link MockGraphQlServer} consumers inject to stub responses. */
  public static final String MOCK_GRAPHQL_SERVER = "mockGraphQlServer";

  /** Bean name for the {@link ApqRegistry} that manages APQ two-phase registration/lookup. */
  public static final String APQ_REGISTRY = "apqRegistry";
}
