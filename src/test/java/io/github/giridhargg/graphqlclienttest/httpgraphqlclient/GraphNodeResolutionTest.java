package io.github.giridhargg.graphqlclienttest.httpgraphqlclient;

import static io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.graphnode.GraphNode.list;
import static io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.graphnode.GraphNode.object;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import graphql.ErrorType;
import graphql.schema.DataFetchingEnvironment;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import io.github.giridhargg.graphqlclienttest.mockmanager.ResolvingErrors;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.graphnode.GraphNode;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.graphnode.Resolver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.FieldAccessException;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Flux;

// STEP 1: Client auto-configuration
@HttpGraphQlClientTest
// specify the graphql schema location or else provide all the properties in
// 'classpath:graphql-client-test.properties'
// Look at 'io.github.giridhargg.graphqlclienttest.graphqlengine.GraphQlStaticTestAssetsProperties'
// for all the properties
class GraphNodeResolutionTest {

  MockGraphQlServer graphQlServer;
  HttpGraphQlClient graphQlClient;

  GraphNodeResolutionTest(
      @Autowired MockGraphQlServer graphQlServer, @Autowired HttpGraphQlClient graphQlClient) {
    this.graphQlServer = graphQlServer;
    this.graphQlClient = graphQlClient;
  }

  @Test
  void testValidaQuery() {
    var id_field = "id";
    var name_field = "name";
    var id_value = "test_id";
    var name_value = "test_name";
    var root_query = "bookById";

    var book = GraphNode.of(root_query, GraphNode.of(id_field, id_value, name_field, name_value));

    graphQlServer.resolveFrom(book);

    var query =
        """
        query SampleQueryOperation {
            bookById(id: "test_id") {
                id
                name
            }
        }
        """;

    var response = graphQlClient.document(query).executeSync();
    var model = response.field("bookById").toEntity(TestBookModel.class);
    assertThat(model)
        .extracting(TestBookModel::id, TestBookModel::name)
        .containsExactly(id_value, name_value);
  }

  @Test
  void testValidaQueryWithResolvers() {
    var id_field = "id";
    var name_field = "name";
    var id_value = "test_id";
    var name_value = "test_name";
    var root_query = "bookById";

    var book =
        GraphNode.of(
            root_query,
            GraphNode.of(
                id_field,
                id_value,
                name_field,
                name_value,
                "author",
                GraphNode.of(
                    name_field, (Resolver) (DataFetchingEnvironment env) -> "author_name")));

    graphQlServer.resolveFrom(book);

    var query =
        """
        query SampleQueryOperation {
            %s(id: "test_id") {
                id
                name
                author {
                    name
                }
            }
        }
        """
            .formatted(root_query);

    var response = graphQlClient.document(query).executeSync();
    var model = response.field("bookById").toEntity(TestBookModel.class);
    assertThat(model)
        .extracting(TestBookModel::id, TestBookModel::name)
        .containsExactly(id_value, name_value);
  }

  @Test
  void testValidaMutationQuery() {
    var id_field = "id";
    var name_field = "name";
    var id_value = "test_id";
    var name_value = "test_name";
    var root_query = "updateBookById";

    var book = GraphNode.of(root_query, GraphNode.of(id_field, id_value, name_field, name_value));

    graphQlServer.resolveFrom(book);

    var query =
        """
        mutation SampleMutationOperation {
            %s(id: "test_id") {
                id
                name
            }
        }
        """
            .formatted(root_query);
    var response = graphQlClient.document(query).executeSync();

    var model = response.field(root_query).toEntity(TestBookModel.class);
    assertThat(model)
        .extracting(TestBookModel::id, TestBookModel::name)
        .containsExactly(id_value, name_value);
  }

  @Test
  void testValidaSubscriptionQuery() {
    var id_field = "id";
    var name_field = "name";
    var id_value = "test_id";
    var name_value = "test_nameeeee";
    var root_query = "subscribeToBook";

    var book =
        GraphNode.of(
            root_query, Flux.just(GraphNode.of(id_field, id_value, name_field, name_value)));

    graphQlServer.resolveFrom(book);

    var query =
        """
        subscription SampleMutationOperation {
            %s {
                id
                name
            }
        }
        """
            .formatted(root_query);
    var response =
        graphQlClient
            .document(query)
            .retrieveSubscription(root_query)
            .toEntity(TestBookModel.class)
            .blockFirst();

    assertThat(response)
        .extracting(TestBookModel::id, TestBookModel::name)
        .containsExactly(id_value, name_value);
  }

  @Test
  void testInvalidQuery() {
    var invalidField = "namename";
    var query =
        """
        query SampleQueryOperation {
            bookById(id: "test_id") {
                id
                %s
            }
        }
        """
            .formatted(invalidField);

    var failureResponse = graphQlClient.document(query).executeSync();
    assertThat(failureResponse.getErrors().isEmpty()).isFalse();
    var error = failureResponse.getErrors().getFirst();
    assertThat(error.getErrorType()).isEqualTo(ErrorType.ValidationError);
    assertThat(error.getMessage()).contains("Validation error", invalidField);
  }

  @Test
  void simulateQueryResolverErrorsFromGraphQlServer() {
    // STEP 4: Provide mock stubs
    var book = GraphNode.of("bookById", new RuntimeException("Something went wrong"));
    graphQlServer.resolveFrom(book);

    var query =
        """
        query SampleQueryOperation {
            bookById(id: "test_id") {
                id
                name
            }
        }
        """;

    var errorResponse = graphQlClient.document(query).executeSync();
    assertThat(errorResponse.getErrors().isEmpty()).isFalse();
    var error = errorResponse.getErrors().getFirst();
    assertThat(error.getErrorType().toString()).isEqualTo("DataFetchingException");
  }

  @Test
  void nestedPathBasedResolvingWithTypeSafeResolutionStrategy() {

    var bookTree =
        object()
            .field(
                "bookById",
                object()
                    .field("id", "test_id")
                    .field("name", "Test Book")
                    .field(
                        "author",
                        object()
                            .field("name", "Jane Doe")
                            .field("bio", ResolvingErrors.notFound("Bio unavailable")))
                    .field(
                        "reviews",
                        list(
                            object().field("rating", 5),
                            object()
                                .field(
                                    "rating",
                                    ResolvingErrors.dataFetchingException("Review failed")))));

    graphQlServer.resolveFrom(bookTree);

    var query =
        """
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
    // assertThat(bioField.getValue()).isNull(); // raw value is null
    assertThat(bioField.getErrors()).isNotEmpty(); // errors present for this field path

    // Or, if you just want to confirm it throws:
    assertThatExceptionOfType(FieldAccessException.class)
        .isThrownBy(() -> bioField.toEntity(String.class));

    var ratingField = response.field("bookById.reviews[1].rating");
    // assertThat(ratingField.getValue()).isNull();
    assertThat(ratingField.getErrors()).isNotEmpty();

    var errors = response.getErrors();
    assertThat(errors).hasSize(2);
    assertThat(errors.stream().map(ResponseError::getMessage))
        .containsExactlyInAnyOrder("Bio unavailable", "Review failed");
  }

  @Test
  void testPersistedQuery() {
    graphQlServer.resolveFrom(
        GraphNode.of("bookById", GraphNode.of("id", "sample_id", "name", "sample_name")));
    var book =
        graphQlClient
            .document("")
            .extensions(
                Map.of(
                    "persistedQuery",
                    Map.of("sha256Hash", "3f5e8a1c9b2d4e6f7a8b9c0d1e2f3a4b", "version", 1)))
            .executeSync()
            .field("bookById")
            .toEntity(TestBookModel.class);
    assertThat(book)
        .extracting(TestBookModel::id, TestBookModel::name)
        .containsExactly("sample_id", "sample_name");
  }

  @Test
  void testAPQ() {
    var id_field = "id";
    var name_field = "name";
    var id_value = "test_id";
    var name_value = "test_name";
    var root_query = "bookById";

    var book = GraphNode.of(root_query, GraphNode.of(id_field, id_value, name_field, name_value));

    graphQlServer.resolveFrom(book);

    var query =
        """
        query SampleQueryOperation {
            bookById(id: "test_id") {
                id
                name
            }
        }
        """;
    Map<String, Object> extensions =
        Map.of("persistedQuery", Map.of("sha256Hash", sha256Hex(query)));

    var response = graphQlClient.document(query).extensions(extensions).executeSync();
    var model = response.field("bookById").toEntity(TestBookModel.class);
    assertThat(model)
        .extracting(TestBookModel::id, TestBookModel::name)
        .containsExactly(id_value, name_value);
  }

  private String sha256Hex(String input) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  record TestBookModel(
      @Nullable String id,
      @Nullable String name,
      @Nullable Integer pageCount,
      @Nullable Author author) {}

  record Author(@Nullable String id, @Nullable String firstName, @Nullable String lastName) {}
}
