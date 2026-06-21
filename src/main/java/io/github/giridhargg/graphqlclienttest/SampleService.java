package io.github.giridhargg.graphqlclienttest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
class SampleService {

    @Autowired
    private HttpGraphQlClient httpGraphQlClient;

    ClientGraphQlResponse validQuery() {
        var query = """
                query SampleQueryOperation {
                    bookById(id: "test_id") {
                        id
                        name
                    }
                }
                """;

        return httpGraphQlClient.document(query).executeSync();
    }

    ClientGraphQlResponse invalidQuery() {
        var invalidField = "namename";
        var query = """
                query SampleQueryOperation {
                    bookById(id: "test_id") {
                        id
                        %s
                    }
                }
                """.formatted(invalidField);

        return httpGraphQlClient.document(query).executeSync();
    }

    ClientGraphQlResponse mutationQuery() {
        var query = """
                mutation SampleMutationOperation {
                    updateBookById(id: "test_id") {
                        id
                        name
                    }
                }
                """;

        return httpGraphQlClient.document(query).executeSync();
    }

    ClientGraphQlResponse loadFromPersistedQuery() {
        return httpGraphQlClient.document("")
                .extensions(Map.of("persistedQuery", Map.of(
                        "sha256Hash", "3f5e8a1c9b2d4e6f7a8b9c0d1e2f3a4b",
                        "version", 1)))
                .executeSync();
    }
}
