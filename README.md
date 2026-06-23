# graphql-client-test

A Spring Boot test library for testing **GraphQL client applications** — the consumer side of a
GraphQL API — analogous to what `MockRestServiceServer` does for plain REST clients, but for
GraphQL.

If your application calls out to a GraphQL API (via `HttpGraphQlClient` or your own service layer
built on top of it) and you want to test that calling code without standing up a real downstream
GraphQL server, this library is for you.

## Why this exists

Testing code that calls a GraphQL API is awkward with general-purpose HTTP mocking tools: you end
up hand-writing JSON response bodies, juggling GraphQL's partial-success/partial-error envelope,
and re-implementing query parsing if you want your stubs to behave differently for different
queries. This library lets you describe a stubbed response the same way you'd describe the data
itself — as a typed object tree or as a set of per-field resolver functions — and handles request
parsing, persisted queries, error path enrichment, and subscriptions for you.

## Two ways to test, one stubbing API

This library supports two distinct testing styles. Both share the exact same consumer-facing
stubbing API (`MockGraphQlServer`), so switching between them later requires no change to how you
write expectations — only which annotation you put on the test class.

| | `@HttpGraphQlClientTest`            | `@EnableGraphQlServerTestConfiguration`                                     |
|---|-------------------------------------|-----------------------------------------------------------------------------|
| What runs | Nothing — entirely in-memory        | A real, local Spring GraphQL server                                         |
| Network calls | None                                | Real HTTP, typically via a proxy (e.g. WireMock) pointed at the local server |
| Speed | Fastest                             | real local server + real HTTP round-trip                                    |
| Use when | Unit testing outbound GraphQl Layer | Testing end-to-end application flow with outbound requests being mocked     |
| Request-matching (`expect`/`expectExact`) | ✅ Supported                         | ❌ Not currently wired — see [Known limitations](#known-limitations)         |

See the [usage guide](USAGE_GUIDE.md) for complete worked examples of both.

## Installation

Add this library as a `testFixtures` (or `testImplementation`) dependency. See your build's
dependency configuration for `io.github.giridhar:<artifact-name>`.

## Quickstart: in-memory unit test

```java
@HttpGraphQlClientTest
class BookClientTest {

    @Autowired MockGraphQlServer graphQlServer;
    @Autowired HttpGraphQlClient graphQlClient;

    @Test
    void fetchesBook() {
        graphQlServer.resolveFrom(Query.of("bookById",
                Query.of("id", "1", "name", "Dune")));

        var response = graphQlClient.document("{ bookById(id:\"1\") { id name } }")
                .executeSync();

        assertThat(response.field("bookById.name").toEntity(String.class))
                .isEqualTo("Dune");
    }
}
```

No server starts, no network call is made, and `MockGraphQlServer`'s queue is reset automatically
before every test method.

## Quickstart: full integration test

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock // enables wiremock server
@EnableGraphQlServerTestConfiguration // brings graphql-server auto-configuration
class BookServiceIntegrationTest {

    @LocalServerPort private int localServerPort;
  
    @Autowired MockGraphQlServer graphQlServer;
    @Autowired MyBookService bookService; // your real production bean, untouched

    @BeforeEach
    public void setup() {
        // NOTE: Your GraphQlClient must be configured to send requests to wiremock. See USAGE_GUIDE.md for full setup.
      // Setup wiremock as proxy to forward the request to local spring-graphql server
      stubFor(
              post(urlPathMatching("/graphql"))
                      .willReturn(
                              aResponse()
                                      .proxiedFrom("http://localhost:" + localServerPort)));
    }

    @Test
    void fetchesBook() {
        graphQlServer.resolveFrom(Query.of("bookById",
                Query.of("id", "1", "name", "Dune")));

        var book = bookService.findBook("1");

        assertThat(book.name()).isEqualTo("Dune");
    }
}
```

A real Spring GraphQL server starts locally, on a random port, and serves every request through
the same `MockGraphQlServer` stubbing API. Note that `@EnableGraphQlServerTestConfiguration` doesn't bring
any graphql-client beans. Its `@SpringBootTest`'s responsibility to autowire all of those.

## Rules consumers must follow

These aren't suggestions — skipping any of them will produce confusing failures (usually "no data
fetcher wired" or "schema not found" type errors) rather than a clear error message pointing at
the missing step.

### 2. Don't register your own `@SchemaMapping` resolvers against the test schema

Both testing styles register a single, unified data fetcher for **every field of every
non-introspection object type** in the compiled schema — not just root `Query`/`Mutation`/
`Subscription` fields. This is what makes nested partial-success/partial-error stubbing and
per-field resolvers work. If your test configuration also registers its own `@SchemaMapping`
controller against the same schema, the two will conflict (typically: graphql-java errors about a
data fetcher being registered twice, or your stubs silently being ignored).

### 3. Schema discovery is automatic — know where it looks

https://docs.spring.io/spring-boot/reference/web/spring-graphql.html

By default, the schema is discovered from any `*.graphqls`/`*.graphql` file found anywhere on the
test classpath (`classpath*:graphql/**/*.graphqls,classpath*:graphql/**/*.graphql`). Multiple matching files are
merged via schema stitching — useful for splitting a large schema across files, but it means two
files defining the same type will fail to merge. Override the location via the
`graphql.test.assets.schema-location` property (comma-separated patterns supported) if your schema
lives somewhere non-standard, isn't picked up automatically, or you need to scope it to avoid
picking up an unrelated schema file elsewhere on the classpath.

### 4. Per-test isolation is automatic — don't add `@DirtiesContext` for this

`MockGraphQlServer`'s queue is reset before every test method automatically. You do not need
`@DirtiesContext`, and adding it will only slow your tests down (full context reload) without
providing any additional isolation this library doesn't already give you.

### 5. Queueing is FIFO and one record per request

Each call to `resolveFrom(...)` (or `expect(...).andResolveFrom(...)`) appends one stub to a
queue; each *completed* outbound GraphQL request consumes exactly one stub, in registration order.
For subscriptions specifically, "completed" means the event stream has terminated — not when it
was first established — so a subscription's stub remains active for the entire lifetime of that
subscription, however many events it emits.

If your code under test makes more outbound requests in a single test than you've queued stubs
for, the data fetcher has nothing to resolve against and every field will return `null`.

### 6. Persisted queries are opt-in and follow a specific naming convention

If you use persisted queries, three pieces must line up by **operation name**:

- A hash mapping file (`persisted-query-hashes.properties` or `.yml`/`.yaml`) with entries shaped
  like `graphql.persisted-query.<DocumentName>.hash=<sha256>` (or the equivalent nested YAML key).
- A `.gql` file containing the operation's document text, named `<DocumentName>.gql`, found under
  the `graphql.test.assets.persisted-queries-pattern` location (default:
  `classpath*:persisted-queries/*.gql`).
- Optionally, a versioned filename `<DocumentName>_V<n>.gql` — the version suffix is stripped
  when matching against the hash file, so you can keep historical versions of a persisted query
  document around without them colliding.

If a `.gql` file's base name has no matching hash entry, asset loading fails fast at startup with
an explicit error naming the missing hash key — this is intentional, since a silently-skipped
persisted query is a much harder failure to diagnose later.


## Resolution strategies at a glance

Two ways to describe a stubbed response; pick whichever fits the test better. See the
[usage guide](USAGE_GUIDE.md) for full examples of each, including partial-error scenarios,
subscriptions, and request-matching.

- **`GraphNode` tree** — a static, typed tree mirroring the response shape, including the root
  field. Best for fixed fixtures.
- **`TypeNodeRegistry`** (via `TypeNode.builder()`) — lazy resolver functions per
  `(type, field)`, with full access to the parent's resolved value and the incoming arguments.
  Best when a field's value needs to be computed rather than hard-coded.

Both support GraphQL-spec partial errors at any depth (`GraphQlErrors`/`SpringGraphQlErrors`) with
automatic path/location enrichment, and both are drained from the same FIFO queue.

## Known limitations

- **`expect`/`expectExact` request-matching is in-memory-only.** `MockGraphQlServer.expect(...)`
  and `.expectExact(...)` let you assert on the shape of the outbound `ExecutionInput` (query,
  variables, operation name, extensions) before returning a stubbed response. This is currently
  only wired into the in-memory connector used by `@HttpGraphQlClientTest`. The real-server
  integration path (`@EnableGraphQlServerTestConfiguration`) does not yet call
  `MockGraphQlServer.matchExecutionInput(...)`, so `expect`/`expectExact` expectations registered
  in an integration test are silently never checked. Use `resolveFrom(...)` directly for
  integration tests, or assert on the request shape via your proxy layer (e.g. WireMock request
  matchers) instead.
- **No JPMS `module-info.java`.** See [Module system support](#module-system-support) below for
  why, and what that means for you.

## Module system support

This library does not ship a `module-info.java`, and that's a deliberate choice rather than an
oversight. Two of its core runtime dependencies make a real module descriptor unsafe to write
right now:

- **graphql-java** ships no `module-info.java` and, as of this writing, no stable
  `Automatic-Module-Name` manifest entry either (tracked upstream as
  [graphql-java/graphql-java#2006](https://github.com/graphql-java/graphql-java/issues/2006)).
  Without a declared name, its module name on the module path is derived from the jar's filename
  — which is not guaranteed stable across versions or repackaging, making any `requires` clause
  pointing at it liable to silently break on a routine dependency bump.
- **Spring Framework** has explicitly deprioritized full JPMS module support; Spring's dependency
  injection and AOP machinery is reflection-heavy in ways that are fundamentally in tension with
  JPMS's strong encapsulation model, and the Spring team has stated there has been limited demand
  for it.

Given both, declaring this library as a proper JPMS module would mean depending on at least one
unstable, filename-derived automatic module name — a worse outcome than staying on the classpath.
If you consume this library from your own modularized application, treat it (and its transitive
dependencies) as classpath/automatic-module dependencies rather than `requires`-ing them by name.

## More

See the [usage guide](USAGE_GUIDE.md) for complete, runnable-shaped examples covering: queries and
mutations with both resolution strategies, nested partial errors, subscriptions, persisted
queries, request-matching, and multi-operation documents.