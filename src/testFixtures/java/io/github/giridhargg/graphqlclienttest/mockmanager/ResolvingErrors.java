package io.github.giridhargg.graphqlclienttest.mockmanager;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import java.util.List;

public class ResolvingErrors {

  /** A {@link ErrorType#ValidationError} — simulates a query that fails schema validation. */
  public static GraphQLError validationError() {
    return error(ErrorType.ValidationError, ErrorType.ValidationError.toString());
  }

  /** A {@link ErrorType#ValidationError} — simulates a query that fails schema validation. */
  public static GraphQLError validationError(String message, String... path) {
    return error(ErrorType.ValidationError, message, path);
  }

  /** A {@link ErrorType#ExecutionAborted} — simulates execution being aborted mid-request. */
  public static GraphQLError executionAborted() {
    return error(ErrorType.ExecutionAborted, ErrorType.ExecutionAborted.toString());
  }

  /** A {@link ErrorType#ExecutionAborted} — simulates execution being aborted mid-request. */
  public static GraphQLError executionAborted(String message, String... path) {
    return error(ErrorType.ExecutionAborted, message, path);
  }

  /** A {@link ErrorType#DataFetchingException} — simulates an unhandled resolver-level failure. */
  public static GraphQLError dataFetchingException() {
    return error(ErrorType.DataFetchingException, ErrorType.DataFetchingException.toString());
  }

  /** A {@link ErrorType#DataFetchingException} — simulates an unhandled resolver-level failure. */
  public static GraphQLError dataFetchingException(String message, String... path) {
    return error(ErrorType.DataFetchingException, message, path);
  }

  /** A {@code NOT_FOUND} error — the requested resource does not exist. */
  public static GraphQLError notFound() {
    return error(
        org.springframework.graphql.execution.ErrorType.NOT_FOUND,
        org.springframework.graphql.execution.ErrorType.NOT_FOUND.toString());
  }

  /** A {@code NOT_FOUND} error — the requested resource does not exist. */
  public static GraphQLError notFound(String message, String... path) {
    return error(org.springframework.graphql.execution.ErrorType.NOT_FOUND, message, path);
  }

  /** An {@code UNAUTHORIZED} error — the caller is not authenticated. */
  public static GraphQLError unauthorized() {
    return error(
        org.springframework.graphql.execution.ErrorType.UNAUTHORIZED,
        org.springframework.graphql.execution.ErrorType.UNAUTHORIZED.toString());
  }

  /** An {@code UNAUTHORIZED} error — the caller is not authenticated. */
  public static GraphQLError unauthorized(String message, String... path) {
    return error(org.springframework.graphql.execution.ErrorType.UNAUTHORIZED, message, path);
  }

  /** A {@code FORBIDDEN} error — the caller is authenticated but lacks permission. */
  public static GraphQLError forbidden() {
    return error(
        org.springframework.graphql.execution.ErrorType.FORBIDDEN,
        org.springframework.graphql.execution.ErrorType.FORBIDDEN.toString());
  }

  /** A {@code FORBIDDEN} error — the caller is authenticated but lacks permission. */
  public static GraphQLError forbidden(String message, String... path) {
    return error(org.springframework.graphql.execution.ErrorType.FORBIDDEN, message, path);
  }

  /** A {@code BAD_REQUEST} error — the request itself is invalid. */
  public static GraphQLError badRequest() {
    return error(
        org.springframework.graphql.execution.ErrorType.BAD_REQUEST,
        org.springframework.graphql.execution.ErrorType.BAD_REQUEST.toString());
  }

  /** A {@code BAD_REQUEST} error — the request itself is invalid. */
  public static GraphQLError badRequest(String message, String... path) {
    return error(org.springframework.graphql.execution.ErrorType.BAD_REQUEST, message, path);
  }

  /** An {@code INTERNAL_ERROR} — an unclassified server-side failure. */
  public static GraphQLError internalError() {
    return error(
        org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR,
        org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR.toString());
  }

  /** An {@code INTERNAL_ERROR} — an unclassified server-side failure. */
  public static GraphQLError internalError(String message, String... path) {
    return error(org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR, message, path);
  }

  /**
   * An error with an arbitrary {@link ErrorClassification}, for simulating a downstream server's
   * own error codes.
   */
  public static GraphQLError error(ErrorClassification type, String message, String... path) {
    var builder = GraphQLError.newError().errorType(type).message(message);
    if (path.length > 0) builder.path(List.of(path));
    return builder.build();
  }
}
