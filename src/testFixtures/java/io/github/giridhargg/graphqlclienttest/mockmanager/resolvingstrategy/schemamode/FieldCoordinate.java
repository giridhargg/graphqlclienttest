package io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode;

/**
 * Identifies a (typeName, fieldName) pair in the schema - e.g. ("Book", "author")
 * or ("QueryNode", "bookById"). Used as the registration key for per-field resolvers.
 *
 * This mirrors graphql-java's FieldCoordinates but is kept as a separate simple
 * record here to avoid leaking graphql-java types into the consumer-facing API
 * unnecessarily (and for clean equals/hashCode as a map key).
 */
public record FieldCoordinate(String typeName, String fieldName) {

    public static FieldCoordinate of(String typeName, String fieldName) {
        return new FieldCoordinate(typeName, fieldName);
    }

    @Override
    public String toString() {
        return typeName + "." + fieldName;
    }
}