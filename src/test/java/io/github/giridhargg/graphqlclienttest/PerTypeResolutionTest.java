package io.github.giridhargg.graphqlclienttest;

import io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest;
import io.github.giridhargg.graphqlclienttest.mockmanager.MockGraphQlServer;
import io.github.giridhargg.graphqlclienttest.mockmanager.ResolvingErrors;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode.SchemaNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.client.HttpGraphQlClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@HttpGraphQlClientTest(classes = SampleService.class)
public class PerTypeResolutionTest {

    @Autowired MockGraphQlServer graphQlServer;
    @Autowired HttpGraphQlClient graphQlClient;

    @Test
    void validQuery() {
        var registry = SchemaNode.builder()
                .query("bookById", env ->
                    Map.of(
                            "id", "test_id",
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
                .field("bio", env -> ResolvingErrors.notFound("Bio unavailable"))
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

        assertThat(response.field("bookById.id").toEntity(String.class)).isEqualTo("test_id");
        assertThat(response.field("bookById.author.name").toEntity(String.class)).isEqualTo("Author of Test Book");
        assertThat(response.field("bookById.reviews[0].rating").toEntity(Integer.class)).isEqualTo(5);
        assertThat(response.field("bookById.reviews[1].rating").toEntity(Integer.class)).isEqualTo(4);

        var bioField = response.field("bookById.author.bio");
//        assertThat(bioField.getValue()).isNull();
        assertThat(bioField.getErrors()).isNotEmpty();
    }

    @Test
    void testing() {
        var registry = SchemaNode.builder()
                .query("bookById", env -> Map.of())
                .type("Book")
                .field("id", env -> "test_id")
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

    }
}
