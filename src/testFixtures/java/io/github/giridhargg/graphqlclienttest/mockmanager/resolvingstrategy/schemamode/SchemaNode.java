package io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode;


/**
 * Fluent builder for {@link SchemaNodeRegistry}, registered via
 * {@code MockGraphQlServer.resolveFrom(SchemaNodeRegistry)}.
 *
 * <pre>{@code
 * var registry = SchemaNode.builder()
 *         .query("bookById", env -> bookFixture)
 *         .type("Book")
 *             .field("author", env -> authorFixture)
 *             .and()
 *         .type("Author")
 *             .field("bio", env -> SpringGraphQlErrors.notFound("Bio unavailable"))
 *         .build();
 *
 * graphQlServer.resolveFrom(registry);
 * }</pre>
 *
 * <p>{@link #type(String)} returns a {@link TypeScope} so multiple fields on the same type can be
 * registered without repeating the type name; call {@link TypeScope#and()} to return to this
 * builder and continue with a different type or root operation, or call
 * {@link TypeScope#build()} directly as a terminal call when no further registrations are
 * needed.</p>
 */
public final class SchemaNode {

    private final SchemaNodeRegistry registry = new SchemaNodeRegistry();

    private SchemaNode() {}

    public static SchemaNode builder() {
        return new SchemaNode();
    }

    // --- Root operation type shortcuts ---

    public SchemaNode query(String fieldName, Resolver resolver) {
        registry.register("Query", fieldName, resolver);
        return this;
    }

    public SchemaNode mutation(String fieldName, Resolver resolver) {
        registry.register("Mutation", fieldName, resolver);
        return this;
    }

    public SchemaNode subscription(String fieldName, Resolver resolver) {
        registry.register("Subscription", fieldName, resolver);
        return this;
    }

    // --- Generic type/field registration ---

    public TypeScope type(String typeName) {
        return new TypeScope(typeName);
    }

    public SchemaNode field(String typeName, String fieldName, Resolver resolver) {
        registry.register(typeName, fieldName, resolver);
        return this;
    }

    public SchemaNodeRegistry build() {
        return registry;
    }

    public final class TypeScope {
        private final String typeName;

        private TypeScope(String typeName) {
            this.typeName = typeName;
        }

        public TypeScope field(String fieldName, Resolver resolver) {
            registry.register(typeName, fieldName, resolver);
            return this;
        }

        /** Return to the outer builder to switch to a different type or root operation. */
        public SchemaNode and() {
            return SchemaNode.this;
        }

        /** Convenience - terminal build without needing .and() first. */
        public SchemaNodeRegistry build() {
            return SchemaNode.this.build();
        }
    }
}