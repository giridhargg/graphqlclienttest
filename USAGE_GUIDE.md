# Usage Guide

Complete, worked examples for both testing styles. Start with the
[README](README.md) if you haven't already — it covers installation, the
high-level differences between the two testing styles, and the rules every
consumer needs to follow.

- [Setup](#setup)
- [Resolution strategy 1: QueryNode tree](#1-querynode-resolution-strategy)
- [Resolution strategy 2: SchemaNode resolvers](#2-schemanode-resolution-strategy)
- [Errors and partial responses](#errors-and-partial-responses)
- [Subscriptions](#subscriptions)
- [Queueing multiple responses](#queueing-multiple-responses)
- [Request-matching](#request-matching)
- [Persisted queries](#persisted-queries)
- [Multi-operation documents](#multi-operation-documents)
- [Full integration tests](#full-integration-tests)


#### Sample Schema used by this guide:

```graphql
type Query {
    bookById(id: ID): Book
    authorById(id: ID): Author
}

type Mutation {
    updateBookById(id: ID): Book
}

type Subscription {
    subscribeToBook: Book
}

type Book {
    id: ID
    name: String
    pageCount: Int
    author: Author
    reviews: [Review]
}

type Author {
    id: ID
    name: String
    firstName: String
    lastName: String
    bio: String
}

type Review {
    rating: Int
}
```

## Setup

### Add gradle dependency
```groovy
testImplementation("io.github.giridhargg.<artifact>")
integrationTestImplementation("io.github.giridhargg.<artifact>")
```

### In-memory unit tests

`@HttpGraphQlclientTest` brings a set of pre-defined beans that can be autowired into the class under test:
- WebClient.Builder - a test ClientHttpConnector will be attached to this, so if the class under test attaches a new
    ClientHttpConnector inside the class under test, then that overrides the already attached connector and the requests
    will not be interpreted by this test-library.
- WebClient - built from above WebClient.Builder. If this is being autowired in the class under test, then the above
    problem doesn't occur.
- HttpGraphQlClient - built from above WebClient. If this is being autowired in the class under test, then the above
  problem doesn't occur.


Suppose you have the following service class that makes GraphQl http requests using HttpGraphQlClient with WebClient underneath:

```java
@Service
class BookService {
    
    // NOTE: This can be one of those above beans: WebClient.Builder, WebClient, HttpGraphQlClient
    // @Autowired WebClient.Builder webClientBuilder;
    // @Autowired WebClient webClient;
    @Autowired HttpGraphQlClient graphQlClient;
    @Autowired SomeOtherDependentClass someOtherDependentClass;
    
    BookModel fetchBook() {
        var query = """
                query {
                    bookById(id: "test_id") {
                        id
                        name
                        author {
                            name
                            bio
                        }
                        bookReviews: reviews {
                            rating
                        }
                    }
                }
                """;
        return graphQlClient.document(query)
                .executeSync()
                .field("bookById")
                .toEntity(BookModel.class);
                
    }
}
```

### 1. `QueryNode` Resolution Strategy:

Build a static tree mirroring the response shape. The root operation field is included as the first key of the top-level node.

```java
// annotation brought by the test-library
// 'classes' attribute auto-imports the classes specified
@HttpGraphQlClientTest(classes = BookService.class)
class BookServiceTest {

    // This mockito bean is because BookService depends on this to be autowired
    @MockitoBean SomeOtherDependentClass someOtherDependentClass;
    
    // auto-configured brought by the test library
    @Autowired MockGraphQlServer graphQlServer;
    
    // service class under test can be autowired
    @Autowired BookService bookService;

    @Test
    void testBookFetching() {
        var bookStub = QueryNode.of("bookById",
                QueryNode.of(
                        "id", "1",
                        "name", "Dune",
                        "author", QueryNode.of(
                                "name", "Frank Herbert",
                                // partial error
                                "bio", GraphQlErrors.dataFetchingError("Author bio unavailable")),
                        // 'bookReviews' is the alias used in the query for 'reviews' field.
                        // If the query has alias, it is recommended to use aliases here
                        "bookReviews", QueryNode.list(
                                QueryNode.of("rating", 5),
                                QueryNode.of("rating", 4))));
        graphQlServer.resolveFrom(bookStub);

        var bookModel = bookService.fetchBook();
        // assertions...
        
    }
}
```

- If the class under test uses constructor injection of the clients, then you can also autowire any of
  WebClient.Builder, WebClient, HttpGraphQlClient into the test class and instantiate the class under test with the
  respective parameter.
- Unstubbed fields fall back to default property access against whatever the parent resolved to — so you only need to
  stub the fields your test actually cares about, and a partially-built tree still behaves reasonably for the rest.
- If the graphql query has aliases, use the aliases as keys in the response graph tree.
- Checkout the source code of `QueryNode` to use builder pattern as well for building the response tree

### 2. `SchemaNode` Resolution Strategy:

Use this when a field's value needs to be computed — from the parent's already-resolved value, or from the query's arguments — rather than being a fixed value.

```java
@HttpGraphQlClientTest
class BookServiceTest {
    
    // auto-configured by the test library
    @Autowired MockGraphQlServer graphQlServer;
    
    // auto-configured by the test-library
    @Autowired HttpGraphQlClient graphQlClient;

    @Test
    void testBookFetching() {
        // sample query based on above graphql-schema
        var testQuery = """
                query {
                    bookById(id: "test_id") {
                        id
                        name
                        author {
                            name
                            bio
                        }
                        reviews {
                            rating
                        }
                    }
                }
                """;

        // NOTE: This pattern follows the above graphql schema structure with 'ParentType > field' associations.
        // Only one resolver per schema type is allowed.
        // The resolver will receive graphql-java's 'DataFetchingEnvironment', which has full control on the execution
        var registry = SchemaNode.builder()
                .query("bookById", env ->
                        Map.of(
                                "id", env.getArgument("id"),
                                "name", "Test Book1"))
                    .type("Book")
                        .field("author", env -> {
                            Map<String, Object> book = env.getSource();
                            // book.get("name") returns "Test Book1" 
                            return Map.of("name", "Author of " + book.get("name"));
                        })
                        .field("reviews", env -> List.of(
                                Map.of("rating", 5),
                                Map.of("rating", 4)))
                    .and()
                    .type("Author")
                        // partial errors
                        .field("bio", env -> SpringGraphQlErrors.notFound("Bio unavailable"))
                .build();

        graphQlServer.resolveFrom(registry);

        var bookModel = graphQlClient.document(query)
                .executeSync()
                .field("bookById")
                .toEntity(BookModel.class);
        // assertions...
    }
}
```
- This second implementation feels a bit complex, but the resolvers will give full control of the execution, like say
  value to be returned has to be computed based on input value, or based on a value from parent type, etc.
- In the second implementation, specific graphql schema type can be registered only once, meaning the above example
  cannot have two `type("Book")`, `type("Book")` resolver registrations. Think this as if you are building a graphql
  schema, in a graphql-schema you cannot have duplicate schema types. A resolver registered for (typeName, fieldName)
  applies to every instance of that type encountered while resolving the response, however many times or however deeply it's nested — matching how a real @SchemaMapping resolver behaves in production.
- Query aliases doesn't matter here. Remember, the registration is schema based, not query based.

Override the schema location or any other property via the annotation's `properties()`:

```java
@HttpGraphQlClientTest(properties = "graphql.test.assets.schema-location=classpath:my-schema.graphqls")
class BookServiceTest { /* ... */ }
```

## Full integration tests

The stubbing API is identical to the in-memory style — only the setup differs, and your actual
production service/controller code runs unmodified, making real (in-process) HTTP calls to the
locally-started Spring GraphQL server:

```yaml
# spring-boo-graphql properties
spring:
  graphql:
    schema:
      locations: classpath*:graphql/** # Location of GraphQl schema files

# NOTE: your graphql client should be configured with this host and port. The keys can be your own choice.
# Notice that the port is set to wiremock, meaning your graphql client sends request to local wiremock server 
my_graphql_server:
  host: "http://localhost"
  port: ${wiremock.server.port}
  path: "/graphql"

# test library properties
graphql:
  test:
    assets:
      # your graphql schema location - required
      schemaLocation: "classpath*:graphql/**/*.graphqls"
      # persisted queries hashes file location - optional
      persistedQueryHashesLocation: "classpath:persisted-query-hashes.properties,classpath:persisted-query-hashes.yml,classpath:persisted-queryNode-hashes.yaml"
      # persisted graphql documents location - optional
      persistedQueriesPattern: 'classpath*:persisted-queries/*.gql'
```
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableGraphQlServerTestConfiguration  // STEP 1: Bring graphql-server auto-configuration and enable wiremock server
@EnableWireMock(...) // wiremock server
class BookServiceIntegrationTest {

    // STEP 2: Bring local spring boot server (starter by @SpringBootTest) port
    @LocalServerPort private int localServerPort;

    @Autowired MockGraphQlServer graphQlServer;

    @BeforeEach
    public void setup() {
        // STEP 3: Setup wiremock as proxy to forward the graphql request to local spring-graphql server
        stubFor(
                post(urlPathMatching("/graphql"))
                        .willReturn(
                                aResponse()
                                        .proxiedFrom("http://localhost:" + localServerPort)));
    }

    @Test
    void fetchesBook() {
        // STEP 4: stub resolvers
        graphQlServer.resolveFrom(QueryNode.of("bookById", QueryNode.of("id", "1")));
        // exercise the consumer's real controller / service / WebClient here
    }
}
```

If your application talks to the downstream GraphQL API through a proxy layer (e.g. WireMock
configured to forward to the locally-started server), point that proxy at the port this
configuration starts the server on, and everything above still applies unchanged — the stubbing
API has no awareness of, or dependency on, how the request physically reaches the server.

## Everything below applies identically to both styles unless stated otherwise — the only thing that changes is which annotation sits on the test class.

## Errors and partial responses

Any leafNode value — in a `QueryNode` tree or returned from a `Resolver` — can be a
`graphql.GraphQLError`, a `Throwable`, or a `DataFetcherResult`, alongside plain data. This means
a single stubbed response can express partial success and partial error simultaneously, exactly
like a real GraphQL server would emit.

```java
@Test
void partialErrorOnNestedField() {
    graphQlServer.resolveFrom(
            QueryNode.of("bookById",
                    QueryNode.of(
                            "id", "1",
                            "name", "Dune",
                            "author", GraphQlErrors.dataFetchingError("Author service unavailable"))));

    var response = graphQlClient
            .document("{ bookById(id:\"1\") { id name author { name } } }")
            .execute()
            .block();

    assertThat(response.field("bookById.name").toEntity(String.class)).isEqualTo("Dune");
    assertThat(response.getErrors()).hasSize(1);
    assertThat(response.getErrors().getFirst().getPath()).containsExactly("bookById", "author");
}
```

Notice no `path` argument was passed to `GraphQlErrors.dataFetchingError(...)` — it's derived
automatically from the field actually being resolved when the error fires, correctly accounting
for nesting depth and listNode indices. Pass an explicit `String... path` only when you need to
simulate a server reporting an incorrect path.

Two factory classes cover the two error classification systems you're likely to need:

```java
// graphql-java spec error types
GraphQlErrors.validationError("Invalid argument");
GraphQlErrors.executionAborted("Request timed out");
GraphQlErrors.dataFetchingError("Unhandled exception in resolver");
GraphQlErrors.custom("Custom code", myErrorClassification);

// spring-graphql ErrorType classifications - what most Spring-based servers actually emit
SpringGraphQlErrors.notFound("Book not found");
SpringGraphQlErrors.unauthorized("Not authenticated");
SpringGraphQlErrors.forbidden("Not permitted");
SpringGraphQlErrors.badRequest("Invalid request");
SpringGraphQlErrors.internal("Unexpected server error");
```

Throwing a plain exception instead of returning a `GraphQLError` simulates an unhandled resolver
crash (graphql-java converts it into a generic `INTERNAL_ERROR` unless it implements
`GraphQLError` itself):

```java
.field("author", env -> { throw new RuntimeException("boom"); })
```

## Subscriptions

Stub the root subscription field with a `reactor.core.publisher.Flux` of source events.
graphql-java re-runs the selection set once per emitted event, producing one response per event.

```java
@Test
void streamsNewBooks() {
    var events = Flux.just(
            QueryNode.of("id", "1", "name", "Dune"),
            QueryNode.of("id", "2", "name", "Dune Messiah"));

    graphQlServer.resolveFrom(QueryNode.of("bookAdded", events));

    var responses = graphQlClient
            .document("subscription { bookAdded { id name } }")
            .executeSubscription()
            .collectList()
            .block();

    assertThat(responses).hasSize(2);
    assertThat(responses.get(0).field("bookAdded.name").toEntity(String.class))
            .isEqualTo("Dune");
}
```

The same works with the per-field-resolver strategy by having the root subscription resolver
return a `Flux`:

```java
var registry = SchemaNode.builder()
        .subscription("bookAdded", env -> Flux.just(
                Map.of("id", "1", "name", "Dune"),
                Map.of("id", "2", "name", "Dune Messiah")))
        .build();

graphQlServer.resolveFrom(registry);
```

A subscription's queued stub is not consumed until its event stream actually terminates — so it
remains active for the entire lifetime of the subscription regardless of how many events it
emits, and the next queued stub (if any) only applies to the *next* outbound request.

## Queueing multiple responses

Each `resolveFrom(...)` call appends one stub to a FIFO queue; each completed outbound request
consumes one. Use this to simulate multiple sequential round-trips within a single test method —
for example, pagination:

```java
@Test
void paginatesThroughResults() {
    // stub for outbound request 1
    graphQlServer.resolveFrom(QueryNode.of("books",
            QueryNode.of("items", QueryNode.list(QueryNode.of("id", "1")),
                    "hasNextPage", true)));
    
    // stub for outbound request 2 in the same execution scope
    graphQlServer.resolveFrom(QueryNode.of("books",
            QueryNode.of("items", QueryNode.list(QueryNode.of("id", "2")),
                    "hasNextPage", false)));

    var allBooks = bookService.fetchAllBooksAcrossPages(); // makes two outbound requests internally

    assertThat(allBooks).extracting("id").containsExactly("1", "2");
}
```

## Request-matching

> Only supported with `@HttpGraphQlClientTest` in unit-tests. See
> [Known limitations](README.md#known-limitations) in the README — this is not currently wired
> for `@EnableGraphQlServerTestConfiguration`.

Pair a response with an assertion on the shape of the request that should have produced it.
`expect(...)` does a partial match (only non-null fields of the expected input are checked);
`expectExact(...)` requires every field to match.

```java
@Test
void sendsExpectedVariables() {
    var expectedInput = ExecutionInput.newExecutionInput()
            .query("query GetBook($id: ID!) { bookById(id: $id) { name } }")
            .variables(Map.of("id", "1"))
            .build();

    graphQlServer.expect(expectedInput)
            .andResolveFrom(QueryNode.of("bookById", QueryNode.of("name", "Dune")));

    graphQlClient
            .documentName("GetBook")
            .variable("id", "1")
            .retrieveSync("bookById")
            .toEntity(Map.class);

    // if the actual request's variables didn't contain id=1, the test fails here
    // with an AssertJ recursive-comparison diff
}
```

A mismatch fails the test immediately, at request time, with an AssertJ-generated diff — not as a
separate manual assertion you have to remember to write.

## Persisted queries

Three pieces, matched by **document name** (see [README rule 6](README.md#6-persisted-queries-are-opt-in-and-follow-a-specific-naming-convention)
for the full constraint):

`src/test/resources/persisted-query-hashes.properties`:

Format: `graphql.persisted-query.<DOCUMENT_NAME>.hash=Hash-Code-asj989808`

```properties
graphql.persisted-query.GetBook.hash=3f5e8a1c9b2d4e6f7a8b9c0d1e2f3a4b
```

or the YAML equivalent, `persisted-query-hashes.yml`:
```yaml
graphql:
  persisted-query:
    GetBook:
      hash: 3f5e8a1c9b2d4e6f7a8b9c0d1e2f3a4b
```

`src/test/resources/persisted-queries/GetBook.gql`:
```graphql
query GetBook($id: ID!) {
    bookById(id: $id) {
        id
        name
    }
}
```

With both in place, no further configuration is needed — a client request carrying
`extensions.persistedQuery.sha256Hash` matching the configured hash resolves to the stored
document text automatically, the same as if the full query text had been sent directly.

## Multi-operation documents

If a single GraphQL document defines more than one named operation, pass `operationName` to
disambiguate — both `GraphQlTestExecutor.executeQuery(...)` overloads and the `ExecutionInput`
built by your client support this directly; no special handling is needed on the stubbing side.

```graphql
query GetBook($id: ID!) { bookById(id: $id) { name } }
query GetAuthor($id: ID!) { authorById(id: $id) { name } }
```

```java
graphQlClient
        .document(documentWithBothOperations)
        .operationName("GetBook")
        .variable("id", "1")
        .executeSync();
```

If a document defines exactly one operation and no `operationName` is given, that sole operation
is used by default, per the GraphQL spec.
