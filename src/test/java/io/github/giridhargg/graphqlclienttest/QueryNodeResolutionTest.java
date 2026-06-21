package io.github.giridhargg.graphqlclienttest;

import graphql.ErrorType;
import io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest;
import io.github.giridhargg.graphqlclienttest.mockmanager.GraphQlErrors;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import io.github.giridhargg.graphqlclienttest.mockmanager.SpringGraphQlErrors;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode.QueryNode;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode.SchemaNode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.FieldAccessException;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode.QueryNode.list;
import static io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode.QueryNode.object;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

// STEP 1: Client auto-configuration
@HttpGraphQlClientTest(classes = SampleService.class)
// specify the graphql schema location or else provide all the properties in 'classpath:graphql-client-test.properties'
// Look at 'io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssetsProperties' for all the properties
class QueryNodeResolutionTest {

    @Autowired
    MockGraphQlServer graphQlServer;

    @Autowired
    SampleService sampleService;

    @Test
    void testValidaQuery() {
        var id_field = "id";
        var name_field = "name";
        var id_value = "test_id";
        var name_value = "test_name";
        var root_query = "bookById";

        var book = QueryNode.of(
                root_query, QueryNode.of(
                                id_field, id_value,
                                name_field, name_value));

//        Map<String, ObjectNode> persistedQueryMetadata = Map.of(
//                "version", 1,
//                "sha256Hash", "sha256Hash");
//        Map<String, ObjectNode> extensions = Map.of(
//                "persistedQuery", persistedQueryMetadata);
//        inMemoryMockGraphQlServer.expect(
//                ExecutionInput.newExecutionInput()
//                        .query(PersistedQuerySupport.PERSISTED_QUERY_MARKER)
//                        .extensions(extensions)
//                        .build())
//                .andRespondWith(book);

        graphQlServer.resolveFrom(book);

        var response = sampleService.validQuery();
        var model = response.field("bookById").toEntity(TestBookModel.class);
        assertThat(model)
                .extracting(
                        QueryNodeResolutionTest.TestBookModel::id,
                        QueryNodeResolutionTest.TestBookModel::name)
                .containsExactly(id_value, name_value);
    }

    @Test
    void testValidaMutationQuery(@Autowired HttpGraphQlClient graphQlClient) {
        var id_field = "id";
        var name_field = "name";
        var id_value = "test_id";
        var name_value = "test_name";
        var root_query = "updateBookById";

        var book = QueryNode.of(
                root_query, QueryNode.of(
                        id_field, id_value,
                        name_field, name_value));

        graphQlServer.resolveFrom(book);

        var query = """
                mutation SampleMutationOperation {
                    %s(id: "test_id") {
                        id
                        name
                    }
                }
                """.formatted(root_query);
        var response = graphQlClient.document(query).executeSync();

        var model = response.field(root_query).toEntity(TestBookModel.class);
        assertThat(model)
                .extracting(
                        QueryNodeResolutionTest.TestBookModel::id,
                        QueryNodeResolutionTest.TestBookModel::name)
                .containsExactly(id_value, name_value);
    }

    @Test
    void testValidaSubscriptionQuery(@Autowired HttpGraphQlClient graphQlClient) {
        var id_field = "id";
        var name_field = "name";
        var id_value = "test_id";
        var name_value = "test_nameeeee";
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

        assertThat(response)
                .extracting(
                        TestBookModel::id,
                        TestBookModel::name)
                .containsExactly(id_value, name_value);
    }

    @Test
    void testInvalidQuery() {
        var failureResponse = sampleService.invalidQuery();
        assertThat(failureResponse.getErrors().isEmpty()).isFalse();
        var error = failureResponse.getErrors().getFirst();
        assertThat(error.getErrorType()).isEqualTo(ErrorType.ValidationError);
        var invalidField = "namename";
        assertThat(error.getMessage()).contains("Validation error", invalidField);
    }

    @Test
    void simulateQueryResolverErrorsFromGraphQlServer() {
        // STEP 4: Provide mock stubs
        var book = QueryNode.of("bookById", new RuntimeException("Something went wrong"));
        graphQlServer.resolveFrom(book);

        var errorResponse = sampleService.validQuery();
        assertThat(errorResponse.getErrors().isEmpty()).isFalse();
        var error = errorResponse.getErrors().getFirst();
        assertThat(error.getErrorType().toString()).isEqualTo("DataFetchingException");
    }

    @Test
    void nestedPathBasedResolvingWithTypeSafeResolutionStrategy(@Autowired HttpGraphQlClient graphQlClient) {

        var bookTree = object()
                .field("bookById", object()
                .field("id", "test_id")
                .field("name", "Test Book")
                .field("author", object()
                        .field("name", "Jane Doe")
                        .field("bio", SpringGraphQlErrors.notFound("Bio unavailable")))
                .field("reviews", list(
                        object().field("rating", 5),
                        object().field("rating", GraphQlErrors.dataFetchingError("Review failed")))));

        graphQlServer.resolveFrom(bookTree);

        var query = """
            query {
                bookById(id: "test_id") {
                    id
                    name
                    author {
                        name
                        bio
                    }
                    reviews {
                        rating
                    }
                }
            }
            """;

        var response = graphQlClient.document(query).executeSync();
        System.out.println("response ===: ");
        System.out.println(response);

        // Successful fields
        assertThat(response.field("bookById.id").toEntity(String.class)).isEqualTo("test_id");
        assertThat(response.field("bookById.author.name").toEntity(String.class)).isEqualTo("Jane Doe");
        assertThat(response.field("bookById.reviews[0].rating").toEntity(Integer.class)).isEqualTo(5);

        // Errored fields - null data, error present
        var bioField = response.field("bookById.author.bio");
//        assertThat(bioField.getValue()).isNull();          // raw value is null
        assertThat(bioField.getErrors()).isNotEmpty();      // errors present for this field path

        // Or, if you just want to confirm it throws:
        assertThatExceptionOfType(FieldAccessException.class)
                .isThrownBy(() -> bioField.toEntity(String.class));

        var ratingField = response.field("bookById.reviews[1].rating");
//        assertThat(ratingField.getValue()).isNull();
        assertThat(ratingField.getErrors()).isNotEmpty();

        var errors = response.getErrors();
        assertThat(errors).hasSize(2);
        assertThat(errors.stream().map(ResponseError::getMessage))
                .containsExactlyInAnyOrder("Bio unavailable", "Review failed");
    }

    @Test
    void perFieldResolverBasedNestedResolution(@Autowired HttpGraphQlClient graphQlClient) {

        var registry = SchemaNode.builder()
                .query("bookById", env -> Map.of(
                        "id", env.getArgument("id"),
                        "name", "Test Book"))
                .type("Book")
                .field("author", env -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> book = env.getSource();
                    return Map.of("name", "Author of " + book.get("name"));
                })
                .field("reviews", env -> List.of(
                        Map.of("rating", 5),
                        Map.of("rating", 4)))
                .and()
                .type("Author")
                .field("bio", env -> SpringGraphQlErrors.notFound("Bio unavailable"))
                .build();

        graphQlServer.resolveFrom(registry);

        var query = """
        query {
            bookById(id: "test_id") {
                id
                name
                author {
                    name
                    bio
                }
                reviews {
                    rating
                }
            }
        }
        """;

        var response = graphQlClient.document(query).executeSync();
        System.out.println("response ===: ");
        System.out.println(response);

        assertThat(response.field("bookById.id").toEntity(String.class)).isEqualTo("test_id");
        assertThat(response.field("bookById.author.name").toEntity(String.class)).isEqualTo("Author of Test Book");
        assertThat(response.field("bookById.reviews[0].rating").toEntity(Integer.class)).isEqualTo(5);
        assertThat(response.field("bookById.reviews[1].rating").toEntity(Integer.class)).isEqualTo(4);

        var bioField = response.field("bookById.author.bio");
//        assertThat(bioField.getValue()).isNull();
        assertThat(bioField.getErrors()).isNotEmpty();
    }

    @Test
    void testPersistedQuery() {
        graphQlServer.resolveFrom(QueryNode.of(
                "bookById", QueryNode.of(
                        "id", "sample_id",
                        "name", "sample_name")));
        var book = sampleService.loadFromPersistedQuery()
                .field("bookById")
                .toEntity(TestBookModel.class);
        assertThat(book)
                .extracting(
                        TestBookModel::id,
                        TestBookModel::name)
                .containsExactly("sample_id", "sample_name");
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
