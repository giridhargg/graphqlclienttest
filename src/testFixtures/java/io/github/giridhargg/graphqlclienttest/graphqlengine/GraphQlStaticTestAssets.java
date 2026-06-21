package io.github.giridhargg.graphqlclienttest.graphqlengine;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM-level cache of compiled GraphQl assets (schema, persisted queries, hash mappings). Assets will be
 * lazily loaded only when {@link #forPaths(GraphQlStaticTestAssetsProperties)} is called with a given
 * set of resource paths.
 *
 * <p>Assets are keyed by the combination of the three configured resources locations, so I/O and
 * schema compilation happen at most once per unique path set per JVM.</p>
 *
 * <p>Loading is lenient: if a configured resource does not exist on the classpath, the corresponding
 * asset is silently empty. This allows consumers that don't use the full test pattern to include
 * {@code @HttpGraphQlClientTest} without providing any static files.</p>
 */
public class GraphQlStaticTestAssets {

    private static final Map<String, Assets> ASSETS_JVM_CACHE = new ConcurrentHashMap<>();

    private GraphQlStaticTestAssets() {}

    /**
     * Loads and caches GraphQl assets for the given resource paths. Subsequent calls with same paths
     * will return the cached assets without reloading from the classpath.
     * @param properties that holds the locations of static files
     * @return Assets that holds the loaded static file contents
     */
    public static Assets forPaths(GraphQlStaticTestAssetsProperties properties) {
        return ASSETS_JVM_CACHE.computeIfAbsent(
                generateKey(properties),
                ignore -> Assets.load(properties));
    }

    private static String generateKey(GraphQlStaticTestAssetsProperties properties) {
        return properties.schemaLocation() + "|"
                + properties.persistedQueryHashesLocation() + "|"
                + properties.persistedQueriesPattern();
    }

    /**
     * Immutable snapshot of all GraphQl assets loaded from one set of resource paths.
     *
     * @param queryHashMappings document name to SHA-256 hash, parsed from the persisted query
     *                          hashes file(s); empty if no such file was found or configured
     * @param persistedQueries  document name to raw GraphQL document text, keyed by the same
     *                          document names as {@code queryHashMappings}; empty if persisted
     *                          queries are not in use
     * @param typeDefinitionRegistry the parsed (but not yet wired) schema; {@code null} if no
     *                          schema file was found on the classpath
     * @param compiledSchema    the executable {@link GraphQLSchema} with a no-op default runtime
     *                          wiring; {@code null} when {@code typeDefinitionRegistry} is
     *                          {@code null}. Callers must wire real data fetchers via
     *                          {@code GraphQLSchema.transform(...)} before executing queries
     *                          against it — see {@link GraphQlTestExecutor}.
     */
    public record Assets(
            Map<String, String> queryHashMappings,
            Map<String, String> persistedQueries,
            TypeDefinitionRegistry typeDefinitionRegistry,
            GraphQLSchema compiledSchema) {

        static Assets load(GraphQlStaticTestAssetsProperties properties) {
            var hashMappings = loadQueryHashMappings(properties.persistedQueryHashesLocation());
            var queries = loadPersistedQueries(properties.persistedQueriesPattern(), hashMappings);
            var registry = loadSchema(properties.schemaLocation());
            var schema = registry != null ? compileSchema(registry) : null;
            return new Assets(hashMappings, queries, registry, schema);
        }

        private static Map<String, String> loadQueryHashMappings(String locations) {
            var resourceLoader = new DefaultResourceLoader();
            var mappings = new HashMap<String, String>();

            for (var location : locations.split(",")) {
                var trimmed = location.trim();
                if (trimmed.isBlank()) continue;

                var resource = resourceLoader.getResource(trimmed);
                if (!resource.exists()) continue;

                try {
                    if (isYaml(trimmed)) {
                        mappings.putAll(loadFromYaml(resource));
                    } else {
                        mappings.putAll(loadFromProperties(resource));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(
                            "Failed to load persisted-query-hashes from: " + trimmed, e);
                }
            }

            return Collections.unmodifiableMap(mappings);
        }

        private static boolean isYaml(String location) {
            return location.endsWith(".yml") || location.endsWith(".yaml");
        }

        private static Map<String, String> loadFromProperties(Resource resource) throws IOException {
            var mappings = new HashMap<String, String>();
            var props = new Properties();
            try (var in = resource.getInputStream()) {
                props.load(in);
            }
            var hashSuffix = ".hash";
            for (var name : props.stringPropertyNames()) {
                if (name.endsWith(hashSuffix)) {
                    var opName = name.substring(
                            "graphql.persisted-query.".length(),
                            name.length() - hashSuffix.length());
                    mappings.put(opName, props.getProperty(name));
                }
            }
            return mappings;
        }

        private static Map<String, String> loadFromYaml(Resource resource) {
            var yamlFactory = new YamlPropertiesFactoryBean();
            yamlFactory.setResources(resource);
            var props = yamlFactory.getObject();

            var mappings = new HashMap<String, String>();
            if (props == null) return mappings;

            var hashSuffix = ".hash";
            for (var name : props.stringPropertyNames()) {
                if (name.endsWith(hashSuffix)) {
                    var opName = name.substring(
                            "graphql.persisted-query.".length(),
                            name.length() - hashSuffix.length());
                    mappings.put(opName, props.getProperty(name));
                }
            }
            return mappings;
        }

        /**
         * Loads persisted query document text, keyed by SHA-256 hash (not by document name),
         * so that {@code PersistedQueryTestInterceptor} can do an O(1) lookup directly from the
         * incoming request's {@code extensions.persistedQuery.sha256Hash}.
         *
         * <p>Each {@code .gql} file's name (minus extension, minus an optional {@code _V<n>}
         * version suffix) must exactly match a document name key in {@code hashMappings}
         * (i.e. the persisted-query-hashes file) — see the README for the full naming
         * convention.</p>
         */
        private static Map<String, String> loadPersistedQueries(
                String pattern, Map<String, String> hashMappings) {
            try {
                var resolver = new PathMatchingResourcePatternResolver();
                var resources = resolver.getResources(pattern);
                if (resources.length == 0) {
                    return Collections.emptyMap();
                }
                var queries = new HashMap<String, String>();
                for (var resource : resources) {
                    var filename = resource.getFilename();
                    if (filename != null && filename.endsWith(".gql")) {
                        var baseName = filename.replaceFirst("\\.gql$", "");
                        var operationName  = baseName.replaceFirst("(_V\\d+)$", "");
                        try (var in = resource.getInputStream()) {
                            var content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            var hash = Objects.requireNonNull(hashMappings.get(operationName ),
                                    "No hash mapping found for query file: " + filename
                                            + " (expected a 'graphql.persisted-query."
                                            + operationName + ".hash' entry)");
                            queries.put(hash, content);
                        }
                    }
                }
                return Collections.unmodifiableMap(queries);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to load persisted queries from pattern: " + pattern, e);
            }
        }

        private static TypeDefinitionRegistry loadSchema(String location) {
            try {
                var resolver = new PathMatchingResourcePatternResolver();
                var registry = new TypeDefinitionRegistry();
                var parser = new SchemaParser();
                var matched = false;
                var seenUrls = new java.util.HashSet<String>();

                for (var pattern : location.split(",")) {
                    var trimmed = pattern.trim();
                    if (trimmed.isBlank()) continue;

                    var resources = resolver.getResources(trimmed);
                    for (var resource : resources) {
                        if (!resource.exists()) continue;

                        // Deduplicate by URL - same file can appear via multiple classpath entries
                        var url = resource.getURL().toString();
                        if (!seenUrls.add(url)) continue;

                        registry.merge(parser.parse(
                                resource.getContentAsString(StandardCharsets.UTF_8)));
                        matched = true;
                    }
                }

                return matched ? registry : null;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load GraphQl schema from: " + location, e);
            }
        }

        /**
         * Compiles the parsed schema into an executable {@link GraphQLSchema} with an empty
         * {@link RuntimeWiring} — no data fetchers are registered here. Callers wire their own
         * (test-specific) data fetchers cheaply per {@code ApplicationContext} via
         * {@code GraphQLSchema.transform(...)}, reusing this compiled instance rather than
         * re-parsing the schema for every test class.
         *
         * @param registry the parsed schema
         * @return an executable schema with no real data fetchers registered
         */
        private static GraphQLSchema compileSchema(TypeDefinitionRegistry registry) {
            return new SchemaGenerator()
                    .makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build());
        }
    }
}
