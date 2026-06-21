package io.github.giridhargg.graphqlclienttest.mockmanager;

import graphql.ErrorClassification;
import graphql.GraphQLError;

import java.util.List;


/**
 * Convenience factories for stubbing errors classified with spring-graphql's
 * {@link org.springframework.graphql.execution.ErrorType}, the classification most real
 * Spring-based GraphQL servers actually emit (as opposed to {@code graphql-java}'s own
 * {@link graphql.ErrorType}, covered by {@link GraphQlErrors}).
 *
 * <p>As with {@link GraphQlErrors}, the {@code path} argument is optional — when omitted, it is
 * derived automatically from the field being resolved when the error fires.</p>
 */
public final class SpringGraphQlErrors {

    /** A {@code NOT_FOUND} error — the requested resource does not exist. */
    public static GraphQLError notFound(String message, String... path) {
        return error(org.springframework.graphql.execution.ErrorType.NOT_FOUND, message, path);
    }

    /** An {@code UNAUTHORIZED} error — the caller is not authenticated. */
    public static GraphQLError unauthorized(String message, String... path) {
        return error(org.springframework.graphql.execution.ErrorType.UNAUTHORIZED, message, path);
    }

    /** A {@code FORBIDDEN} error — the caller is authenticated but lacks permission. */
    public static GraphQLError forbidden(String message, String... path) {
        return error(org.springframework.graphql.execution.ErrorType.FORBIDDEN, message, path);
    }

    /** A {@code BAD_REQUEST} error — the request itself is invalid. */
    public static GraphQLError badRequest(String message, String... path) {
        return error(org.springframework.graphql.execution.ErrorType.BAD_REQUEST, message, path);
    }

    /** An {@code INTERNAL_ERROR} — an unclassified server-side failure. */
    public static GraphQLError internal(String message, String... path) {
        return error(org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR, message, path);
    }

    private static GraphQLError error(ErrorClassification type, String message, String... path) {
        var builder = GraphQLError.newError().errorType(type).message(message);
        if (path.length > 0) builder.path(List.of(path));
        return builder.build();
    }
}
