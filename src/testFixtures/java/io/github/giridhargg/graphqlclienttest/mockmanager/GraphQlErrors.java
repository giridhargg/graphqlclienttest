package io.github.giridhargg.graphqlclienttest.mockmanager;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode.QueryNode;

import java.util.List;


/**
 * Convenience factories for stubbing {@code graphql-java}-spec errors as leaf values in a
 * {@link QueryNode}
 * tree or as a {@code SchemaNode} return value.
 *
 * <p>Every factory here accepts an optional, variadic {@code path} — when omitted, the error's
 * {@code path} and {@code location} are derived automatically from the field actually being
 * resolved at the moment the error fires (see {@code ResolvingManager.enrichError}), correctly
 * accounting for nesting depth and list indices without the caller needing to compute either.
 * Pass an explicit path only when the auto-derived one would be wrong for your scenario (e.g.
 * simulating a server bug where the reported error path does not match the field that actually
 * failed).</p>
 *
 * @see SpringGraphQlErrors for the spring-graphql {@link org.springframework.graphql.execution.ErrorType}
 *      equivalents (NOT_FOUND, UNAUTHORIZED, etc.)
 */
final class GraphQlErrors {

    /** A {@link ErrorType#ValidationError} — simulates a query that fails schema validation. */
    static GraphQLError validationError(String message, String... path) {
        return GraphQLError.newError()
                .errorType(ErrorType.ValidationError)
                .message(message)
                .path(List.of(path))
                .build();
    }

    /** A {@link ErrorType#ExecutionAborted} — simulates execution being aborted mid-request. */
    static GraphQLError executionAborted(String message) {
        return GraphQLError.newError()
                .errorType(ErrorType.ExecutionAborted)
                .message(message)
                .build();
    }

    /** A {@link ErrorType#DataFetchingException} — simulates an unhandled resolver-level failure. */
    static GraphQLError dataFetchingError(String message, String... path) {
        return GraphQLError.newError()
                .errorType(ErrorType.DataFetchingException)
                .message(message)
                .path(List.of(path))
                .build();
    }

    /** An error with an arbitrary {@link ErrorClassification}, for simulating a downstream server's own error codes. */
    static GraphQLError custom(String message, ErrorClassification classification, String... path) {
        return GraphQLError.newError()
                .errorType(classification)
                .message(message)
                .path(List.of(path))
                .build();
    }
}
