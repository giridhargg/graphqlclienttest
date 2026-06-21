//package io.github.giridhargg.graphqlclienttest.httpsyncgraphqlclient;
//
//import io.github.giridhargg.graphqlclienttest.core.ClientHttpRequestTestFactory;
//import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssets;
//import io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssetsProperties;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Primary;
//import org.springframework.graphql.client.HttpSyncGraphQlClient;
//import org.springframework.web.client.RestClient;
//
//@TestConfiguration
//public class HttpSyncGraphQlClientTestAutoConfiguration {
//
//    public static final String CLIENT_HTTP_REQUEST_TEST_FACTORY = "clientHttpRequestTestFactory";
//    public static final String REST_CLIENT_TEST_BUILDER = "restClientTestBuilder";
//    public static final String REST_TEST_CLIENT = "restTestClient";
//    public static final String HTTP_SYNC_GRAPHQL_TEST_CLIENT = "httpSyncGraphQlTestClient";
//    public static final String GRAPHQL_STATIC_TEST_ASSETS = "graphQlStaticTestAssets";
//    public static final String GRAPHQL_TEST_EXECUTOR = "graphQlTestExecutor";
//
//    @Bean(GRAPHQL_STATIC_TEST_ASSETS)
//    @Primary
//    GraphQlStaticTestAssets.Assets assets(GraphQlStaticTestAssetsProperties properties) {
//        return GraphQlStaticTestAssets.forPaths(properties);
//    }
//
////    @Bean(GRAPHQL_TEST_EXECUTOR)
////    @Primary
////    GraphQlTestExecutor graphQlTestExecutor(
////            MockQueryResolver mockQueryResolver,
////            GraphQlStaticTestAssets.Assets grapQlAssets) {
////        return new GraphQlTestExecutor(mockQueryResolver, grapQlAssets);
////    }
//
//    @Bean(name = CLIENT_HTTP_REQUEST_TEST_FACTORY)
//    @Primary
//    ClientHttpRequestTestFactory clientHttpRequestTestFactory() {
//        return new ClientHttpRequestTestFactory();
//    }
//
//    @Bean(name = REST_CLIENT_TEST_BUILDER)
//    @Primary
//    RestClient.Builder restClientTestBuilder(
//            RestClient.Builder builder,
//            @Qualifier(CLIENT_HTTP_REQUEST_TEST_FACTORY) ClientHttpRequestTestFactory clientHttpRequestTestFactory) {
//        return builder.requestFactory(clientHttpRequestTestFactory);
//    }
//
//    @Bean(REST_TEST_CLIENT)
//    @Primary
//    RestClient restTestClient(@Qualifier(REST_CLIENT_TEST_BUILDER) RestClient.Builder builder) {
//        return builder.build();
//    }
//
//    @Bean(HTTP_SYNC_GRAPHQL_TEST_CLIENT)
//    @Primary
//    HttpSyncGraphQlClient httpSyncGraphQlClient(
//            @Qualifier(REST_TEST_CLIENT) RestClient restClient) {
//        return HttpSyncGraphQlClient.builder(restClient)
//                .build();
//    }
//}
