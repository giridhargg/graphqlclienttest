package io.github.giridhargg.graphqlclienttest.graphqlengine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configures where {@link GraphQlStaticTestAssets} looks for the GraphQL schema and, optionally,
 * persisted query assets.
 *
 * <p>Every property here accepts one or more comma-separated
 * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver} resource
 * patterns (e.g. {@code classpath:}, {@code classpath*:}, with or without {@code *}/{@code **}
 * globs). When a pattern matches multiple resources, all matches are merged.</p>
 *
 * <p>The library ships a bundled {@code graphql-client-test-defaults.properties} on its own classpath
 * which already supplies sensible defaults, loaded automatically by
 * {@link io.github.giridhargg.graphqlclienttest.httpgraphqlclient.HttpGraphQlClientTest @HttpGraphQlClientTest}.
 * The {@link DefaultValue} annotations below are a secondary fallback only. Consumers can override
 * these properties or the default properties file to set locations explicitly.</p>
 *
 * @param schemaLocation comma-separated resource pattern(s) for {@code .graphqls}/{@code .graphql}
 *                        schema files. All matches are merged into a single schema via
 *                        {@code TypeDefinitionRegistry.merge(...)}, so schema-stitching across
 *                        multiple files is supported as long as no two files redefine the same type.
 * @param persistedQueryHashesLocation comma-separated resource pattern(s) for the document-name
 *                        to SHA-256-hash mapping file(s). Both {@code .properties} and
 *                        {@code .yml}/{@code .yaml} formats are supported; missing files are
 *                        silently ignored since persisted queries are an opt-in feature.
 * @param persistedQueriesPattern comma-separated resource pattern(s) for the {@code .gql} files
 *                        containing the actual persisted query documents, one file per document.
 */
@ConfigurationProperties("graphql.test.assets")
public record GraphQlStaticTestAssetsProperties(
        @DefaultValue("classpath*:**/*.graphqls,classpath*:**/*.graphql")
        String schemaLocation,
        @DefaultValue("classpath:persisted-query-hashes.properties,classpath:persisted-query-hashes.yml,classpath:persisted-query-hashes.yaml")
        String persistedQueryHashesLocation,
        @DefaultValue("classpath*:persisted-queries/*.gql")
        String persistedQueriesPattern) {}
