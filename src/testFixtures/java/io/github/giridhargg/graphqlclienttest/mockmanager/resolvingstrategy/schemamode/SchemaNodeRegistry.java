package io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.schemamode;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds per-(type,field) resolver registrations for a single ExecutionRecord.
 * Lookup is by exact (typeName, fieldName) match - applies to every instance
 * of that type encountered during execution, regardless of path/depth/list index.
 */
public final class SchemaNodeRegistry {

    private final Map<FieldCoordinate, Resolver> resolvers = new LinkedHashMap<>();

    public SchemaNodeRegistry register(String typeName, String fieldName, Resolver resolver) {
        resolvers.put(FieldCoordinate.of(typeName, fieldName), resolver);
        return this;
    }

    public SchemaNodeRegistry register(FieldCoordinate coordinate, Resolver resolver) {
        resolvers.put(coordinate, resolver);
        return this;
    }

    @Nullable
    public Resolver lookup(String typeName, String fieldName) {
        return resolvers.get(FieldCoordinate.of(typeName, fieldName));
    }

    public boolean isEmpty() {
        return resolvers.isEmpty();
    }

    public Map<FieldCoordinate, Resolver> asMap() {
        return Map.copyOf(resolvers);
    }
}