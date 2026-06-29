package io.github.giridhargg.graphqlclienttest.graphqlserverconfig;

public class Constants {

  /**
   * Bean name for the {@link io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer}
   * consumers inject to stub responses.
   */
  public static final String MOCK_GRAPHQL_RESOLVER = "mockGraphQlResolver";

  /**
   * Bean name for the {@link org.springframework.graphql.execution.RuntimeWiringConfigurer}
   * registered with Spring GraphQL.
   */
  public static final String RUNTIME_WIRING_CONFIGURER = "runtimeWiringConfigurer";

  /** Bean name for {@link ExecutionRecordPollingInterceptor}. */
  public static final String EXECUTION_POLLING_INTERCEPTOR = "executionRecordPollingInterceptor";

  /**
   * Bean name for the cached {@link
   * io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets.Assets}.
   */
  public static final String GRAPHQL_STATIC_TEST_ASSETS = "graphQlStaticTestAssets";

  /** Bean name for {@link PersistedQueryInterceptor}. */
  public static final String PERSISTED_QUERY_INTERCEPTOR = "persistedQueryInterceptor";

  /**
   * Bean name for the {@link io.github.giridhargg.graphqlclienttest.graphqlengine.ApqRegistry} that
   * manages APQ two-phase registration/lookup.
   */
  public static final String APQ_REGISTRY = "apqRegistry";
}
