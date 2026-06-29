package io.github.giridhargg.graphqlclienttest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class GraphQlClientIntegrationTestApplication {

  @Value("${downstream_graphql_service.integration_test_host}")
  String host;

  @Value("${downstream_graphql_service.integration_test_port}")
  String port;

  public static void main(String[] args) {
    SpringApplication.run(GraphQlClientIntegrationTestApplication.class, args);
  }

  @Bean
  HttpGraphQlClient httpGraphQlClient(
      @Value("${downstream_graphql_service.integration_test_host}") String host,
      @Value("${downstream_graphql_service.integration_test_port}") String port,
      @Value("${downstream_graphql_service.path}") String path) {
    var baseUrl = String.format("%s:%s%s", host, port, path);
    var webClient =
        WebClient.create(baseUrl)
            .mutate()
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
            .build();
    return HttpGraphQlClient.create(webClient);
  }
}
