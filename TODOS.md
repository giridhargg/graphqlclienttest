> TODOS:
- persisted query-hash and files and locations specifications




> Draft codes:

```java
/**
     * Registers a single unified {@link DataFetcher} — delegating to
     * {@link MockGraphQlServer#resolve} — as the default data fetcher for every field of every
     * non-introspection object type in the compiled schema.
     */
    @Primary
    @Bean(Constants.RUNTIME_WIRING_CONFIGURER)
    @DependsOn({Constants.MOCK_GRAPHQL_RESOLVER, GRAPHQL_STATIC_TEST_ASSETS})
    RuntimeWiringConfigurer runtimeWiringTestConfigurer(
            @Qualifier(Constants.MOCK_GRAPHQL_RESOLVER)
            MockGraphQlServer mockGraphQlServer,
            @Qualifier(GRAPHQL_STATIC_TEST_ASSETS)
            GraphQlStaticTestAssets.Assets assets) {

        DataFetcher<?> unifiedFetcher = dfe -> {
            try {
                return mockGraphQlServer.resolve(dfe);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        return wiringBuilder -> {
            for (var type : assets.compiledSchema().getAllTypesAsList()) {
                if (type instanceof GraphQLObjectType objectType
                        && !objectType.getName().startsWith("__")) {
                    wiringBuilder.type(objectType.getName(),
                            typeWiring -> typeWiring.defaultDataFetcher(unifiedFetcher));
                }
            }
        };
    }
```

```groovy
plugins {
    id "com.netflix.dgs.codegen" version '8.3.0'
}


generateJava {
	packageName("io.github.giridhargg.graphqlclienttest.testdto")
	setSchemaPaths(["$projectDir/src/testFixtures/resources/downstream_graphql_service/schema.graphqls"])
	generateInterfaceSetters(false)
	generateClientv2(true)
	setDisableDatesInGeneratedAnnotation(true)
	setAddGeneratedAnnotation(true)
	generateJSpecifyAnnotations(true)
}
```