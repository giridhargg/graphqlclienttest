package io.github.giridhargg.graphqlclienttest.mockmanager.resolvingstrategy.querymode;

import graphql.GraphQLError;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in a typed, nested response tree mirroring the shape of a GraphQL query.
 * A node is one of:
 *  - OBJECT: a map of field name -> QueryNode
 *  - LIST: an ordered list of QueryNode (each typically OBJECT or LEAF)
 *  - LEAF: a terminal value - plain data, a GraphQLError, or a Throwable
 */
public sealed interface QueryNode {

    static ObjectNode object() {
        return new ObjectNode(new LinkedHashMap<>());
    }

    static ListNode list(QueryNode... elements) {
        return new ListNode(new ArrayList<>(List.of(elements)));
    }

    static LeafNode leaf(@Nullable Object value) {
        return new LeafNode(value);
    }

    // --- Convenience factories for common leaf shapes ---

    static LeafNode error(GraphQLError error) {
        return new LeafNode(error);
    }

    static LeafNode error(Throwable throwable) {
        return new LeafNode(throwable);
    }

    /**
     * This is a thin convenience layer on top of the existing .field() machinery
     * @param args
     * @return
     */
    static ObjectNode of(Object... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "QueryNode.of() requires an even number of arguments (key-value pairs), got " + args.length);
        }
        var node = object();
        for (int i = 0; i < args.length; i += 2) {
            if (!(args[i] instanceof String key)) {
                throw new IllegalArgumentException(
                        "Expected a String key at position " + i + ", got: " + args[i]);
            }
            node.field(key, args[i + 1]);
        }
        return node;
    }

    /**
     * OBJECT node - a map of field name to child QueryNode.
     * Field names here are schema/response field names (alias-aware at resolution time),
     * NOT dot-paths.
     */
    record ObjectNode(Map<String, QueryNode> fields) implements QueryNode {

        public ObjectNode field(String name, QueryNode child) {
            fields.put(name, child);
            return this;
        }

        /** Convenience: wrap a plain value as a LeafNode automatically. */
        public ObjectNode field(String name, @Nullable Object plainValue) {
            fields.put(name, toNode(plainValue));
            return this;
        }

        @Nullable
        public QueryNode get(String name) {
            return fields.get(name);
        }

        public boolean has(String name) {
            return fields.containsKey(name);
        }
    }

    /** LIST node - ordered children, typically OBJECT or LEAF nodes. */
    record ListNode(List<QueryNode> elements) implements QueryNode {

        public int size() {
            return elements.size();
        }

        @Nullable
        public QueryNode get(int index) {
            return index < elements.size() ? elements.get(index) : null;
        }
    }

    /** LEAF node - terminal value: data, GraphQLError, or Throwable. */
    record LeafNode(@Nullable Object value) implements QueryNode {
    }

    /**
     * Helper to coerce arbitrary values into QueryNode when consumers pass plain
     * objects/maps/lists instead of explicit builders.
     */
    static QueryNode toNode(@Nullable Object value) {
        if (value instanceof QueryNode node) {
            return node;
        }
        if (value instanceof Map<?, ?> map) {
            var obj = QueryNode.object();
            for (var entry : map.entrySet()) {
                obj.field(String.valueOf(entry.getKey()), toNode(entry.getValue()));
            }
            return obj;
        }
        if (value instanceof java.util.List<?> list) {
            var elements = list.stream().map(QueryNode::toNode).toArray(QueryNode[]::new);
            return QueryNode.list(elements);
        }
        return new LeafNode(value);
    }
}