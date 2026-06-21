package io.github.giridhargg.graphqlclienttest;

import graphql.ErrorType;
import graphql.GraphQLError;
import io.github.giridhargg.graphqlclienttest.graphqlserverconfig.EnableGraphQlServerTestConfiguration;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode.QueryNode;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        classes = GraphQlClientTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integrationTest")
@Import(IntegrationTestBeansAutoConfiguration.class)
@EnableWireMock({
        @ConfigureWireMock(filesUnderDirectory = "src/integrationTest/resources", port = 0)
})
@EnableGraphQlServerTestConfiguration
public class GraphQlClientIntegrationTest {

    private static final String GRAPHQL_PATH = "/graphql";

    @LocalServerPort private int localServerPort;

    @Autowired HttpGraphQlClient httpGraphQlClient;
    @Autowired private MockGraphQlServer graphQlServer;

    @BeforeEach
    public void setup() {
        // STEP 3: Setup wiremock as proxy to forward the request to local spring-graphql server
        stubFor(
                post(urlPathMatching(GRAPHQL_PATH))
                        .willReturn(
                                aResponse()
                                        .proxiedFrom("http://localhost:" + localServerPort)));
    }

    @Test
    void validQueryShouldBeExecutedSuccessfully() {
        var id_field = "id";
        var name_field = "name";
        var id_value = "test_id";
        var name_value = "test_name";
        var root_query = "bookById";

        var book = QueryNode.of(
                root_query, QueryNode.of(
                        id_field, id_value,
                        name_field, name_value));

        graphQlServer.resolveFrom(book);

        var query = """
                query SampleQueryOperation {
                    %s(id: "test_id") {
                        id
                        name
                    }
                }
                """.formatted(root_query);

        var response = executeQuery(query);
        var bookResponse = response.field(root_query).toEntity(Map.class);

        assertNotNull(bookResponse);
        assertThat(bookResponse.get(id_field)).isEqualTo(id_value);
        assertThat(bookResponse.get(name_field)).isEqualTo(name_value);
        verify(1, postRequestedFor(urlPathMatching(GRAPHQL_PATH)));
    }

    @Test
    void testValidaSubscriptionQuery(@Autowired HttpGraphQlClient graphQlClient) {
        var id_field = "id";
        var name_field = "name";
        var id_value = "test_id";
        var name_value = "test_name";
        var root_query = "subscribeToBook";

        var book = QueryNode.of(
                root_query, Flux.just(QueryNode.of(
                        id_field, id_value,
                        name_field, name_value)));

        graphQlServer.resolveFrom(book);

        var query = """
            subscription SampleMutationOperation {
                %s {
                    id
                    name
                }
            }
            """.formatted(root_query);

        var response = graphQlClient.document(query)
                .retrieveSubscription(root_query)
                .toEntity(TestBookModel.class)
                .blockFirst();

        System.out.println("results ===: ");
        System.out.println(response);

        assertThat(response)
                .extracting(TestBookModel::id, TestBookModel::name)
                .containsExactly(id_value, name_value);
    }

    @Test
    void invalidQueryShouldFail() {
        var incorrectField = "namename";
        var query = """
                query SampleQueryOperation {
                    bookById(id: "test_id") {
                        id
                        %s
                    }
                }
                """.formatted(incorrectField);

        var failureResponse = executeQuery(query);

        assertThat(failureResponse.getErrors().isEmpty()).isFalse();
        var error = failureResponse.getErrors().getFirst();
        assertThat(error.getErrorType()).isEqualTo(ErrorType.ValidationError);
        assertThat(error.getMessage()).contains("Validation error", incorrectField);
        verify(1, postRequestedFor(urlPathMatching(GRAPHQL_PATH)));
    }

    @Test
    void simulateQueryResolverErrorsFromGraphQlServer() {
        var root_query = "bookById";

        var book = QueryNode.of(root_query, new RuntimeException("Something went wrong"));

        graphQlServer.resolveFrom(book);

        var query = """
                query SampleQueryOperation {
                    %s(id: "test_id") {
                        id
                        name
                    }
                }
                """.formatted(root_query);

        var errorResponse = executeQuery(query);
        System.out.println("errorResponse ===: " + errorResponse);
        assertThat(errorResponse.getErrors().isEmpty()).isFalse();
        var error = errorResponse.getErrors().getFirst();
        assertThat(error.getErrorType().toString()).isEqualTo("INTERNAL_ERROR");
        verify(1, postRequestedFor(urlPathMatching(GRAPHQL_PATH)));
    }

    @Test
    void resolverErrorsFromGraphQlServer() {
        var root_query = "bookById";

        // STEP 4: Provide mock stubs
        var book = QueryNode.of(
                root_query, GraphQLError.newError()
                .errorType(org.springframework.graphql.execution.ErrorType.NOT_FOUND)
                .message("Did not found")
//                .path(ResultPath.rootPath())
//                .path("some step info path")
                .build(),
                "authorById", GraphQLError.newError()
                        .errorType(org.springframework.graphql.execution.ErrorType.FORBIDDEN)
                        .message("Someting webnt wrong")
//                        .path(ResultPath.rootPath())
//                .path("some step info path")
                        .build());

        graphQlServer.resolveFrom(book);

        var query = """
                query SampleQueryOperation {
                    %s(id: "test_id") {
                        id
                        name
                    }
                    authorById(id: "another_id") {
                        id
                        firstName
                    }
                }
                """.formatted(root_query);

        var errorResponse = executeQuery(query);
        System.out.println("errorResponse ===: " + errorResponse);
//        assertThat(errorResponse.getErrors().isEmpty()).isFalse();
//        var error = errorResponse.getErrors().getFirst();
//        assertThat(error.getErrorType().toString()).isEqualTo("INTERNAL_ERROR");
//        verify(1, postRequestedFor(urlPathMatching(GRAPHQL_PATH)));
    }

    @Test
    void simulateNetworkErrors() {
        stubFor(
                post(urlPathMatching("/graphql"))
                        .willReturn(
                                aResponse().withStatus(HttpStatus.BAD_REQUEST.value())));

        var query = """
                query SampleQueryOperation {
                    bookById(id: "test_id") {
                        id
                        name
                    }
                }
                """;

        assertThatExceptionOfType(WebClientResponseException.BadRequest.class)
                .isThrownBy(() -> executeQuery(query));
        verify(1, postRequestedFor(urlPathMatching(GRAPHQL_PATH)));
    }

    @Test
    void multiplePaginatedOutboundCalls() {

        var id_field = "id";
        var name_field = "name";
        var id_value = "test_id";
        var name_value = "test_name";

        var book1 = QueryNode.of(
                "query1", QueryNode.of(
                        id_field, id_value,
                        name_field, name_value));

        var book2 = QueryNode.of(
                "query2", QueryNode.of(
                        id_field, id_value,
                        name_field, name_value));

        var query1 = """
                query SampleQueryOperation {
                    query1: bookById(id: "test_id") {
                        id
                        name
                    }
                }
                """;

        var query2 = """
                query SampleQueryOperation {
                    query2: bookById(id: "test_id") {
                        id
                        name
                    }
                }
                """;

        // First page
        graphQlServer.resolveFrom(book1);

        // Second page
        graphQlServer.resolveFrom(book2);

        // First outbound call - consumer's business logic triggers this
        var page1 = executeQuery(query1);
        System.out.println("page1 ===: ");
        System.out.println(page1);

        // Second outbound call - same test, queue advances to second ExecutionRecord
        var page2 = executeQuery(query2);
        System.out.println("page2 ===: ");
        System.out.println(page2);

        verify(2, postRequestedFor(urlPathMatching(GRAPHQL_PATH)));
    }

    private @NonNull ClientGraphQlResponse executeQuery(String query) {
        return httpGraphQlClient.document(query).executeSync();
    }


    record TestBookModel(
            @Nullable String id,
            @Nullable String name,
            @Nullable Integer pageCount,
            @Nullable Author author) {}

    record Author(
            @Nullable String id,
            @Nullable String firstName,
            @Nullable String lastName) {}
}
