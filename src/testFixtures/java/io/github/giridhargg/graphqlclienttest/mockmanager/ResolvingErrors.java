package io.github.giridhargg.graphqlclienttest.mockmanager;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;

import java.util.List;

public class ResolvingErrors {

    /** A {@code NOT_FOUND} error — the requested resource does not exist. */
    public static GraphQLError notFound(String message, String... path) {
        return SpringGraphQlErrors.notFound(message, path);
    }

    /** An {@code UNAUTHORIZED} error — the caller is not authenticated. */
    public static GraphQLError unauthorized(String message, String... path) {
        return SpringGraphQlErrors.unauthorized(message, path);
    }

    /** A {@code FORBIDDEN} error — the caller is authenticated but lacks permission. */
    public static GraphQLError forbidden(String message, String... path) {
        return SpringGraphQlErrors.forbidden(message, path);
    }

    /** A {@code BAD_REQUEST} error — the request itself is invalid. */
    public static GraphQLError badRequest(String message, String... path) {
        return SpringGraphQlErrors.badRequest(message, path);
    }

    /** An {@code INTERNAL_ERROR} — an unclassified server-side failure. */
    public static GraphQLError internal(String message, String... path) {
        return SpringGraphQlErrors.internal(message, path);
    }

    private static GraphQLError error(ErrorClassification type, String message, String... path) {
        var builder = GraphQLError.newError().errorType(type).message(message);
        if (path.length > 0) builder.path(List.of(path));
        return builder.build();
    }

    /** A {@link ErrorType#ValidationError} — simulates a query that fails schema validation. */
    public static GraphQLError validationError(String message, String... path) {
        return GraphQlErrors.validationError(message, path);
    }

    /** A {@link ErrorType#ExecutionAborted} — simulates execution being aborted mid-request. */
    public static GraphQLError executionAborted(String message) {
        return GraphQlErrors.executionAborted(message);
    }

    /** A {@link ErrorType#DataFetchingException} — simulates an unhandled resolver-level failure. */
    public static GraphQLError dataFetchingError(String message, String... path) {
        return GraphQlErrors.dataFetchingError(message, path);
    }

    /** An error with an arbitrary {@link ErrorClassification}, for simulating a downstream server's own error codes. */
    public static GraphQLError custom(String message, ErrorClassification classification, String... path) {
        return GraphQlErrors.custom(message, classification, path);
    }
}
