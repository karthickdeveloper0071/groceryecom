# Testing standard

Tools: JUnit 5, Mockito, AssertJ, Spring Boot Test, Spring Security Test, Spring
Modulith Test, and zonky embedded-postgres for a real PostgreSQL. Why a real
database rather than H2:
[ADR-0008](../architecture/adr/0008-real-postgresql-in-tests.md).

## Running tests

```bash
./mvnw verify        # everything: unit tests, context tests, module rules, SpotBugs
./mvnw test          # unit and integration tests only
./mvnw test -Dtest=MoneyTest
```

No Docker needed. The first run on a machine downloads and unpacks the PostgreSQL
binaries, so it is slower than the rest.

`verify` is what CI runs, so a green `verify` locally means a green build.

## Two kinds of test

**Unit tests** do not start Spring. Fast, no database, no context.

Use one for logic you can call directly: `MoneyTest` for arithmetic and rounding,
`JwtTokenProviderTest` for token creation and rejection (it injects a fixed
`Clock` through the package-private constructor, so expiry is testable without
waiting), service tests with Mockito for business rules.

**Context tests** extend `com.groceryecom.PostgresIntegrationTest`, which starts
one real embedded PostgreSQL 17 per test JVM and runs the production Flyway
migrations against it. Use one when the thing under test *is* the wiring: the
security filter chain, a controller's JSON, a migration, a repository query.

| Test | Kind | Covers |
|------|------|--------|
| `MoneyTest` | unit | `Money` arithmetic, currency mismatch, rounding, overflow |
| `JwtTokenProviderTest` | unit | expired, forged, tampered and swapped tokens |
| service tests (Mockito) | unit | business rules without a database |
| `ContextLoadTest` | context | the application context starts and migrations apply |
| `ModularityTest` | plain JUnit, no context | module boundaries and the `shared`/`platform` rule |
| `AuthFlowIntegrationTest` | context | register, login, refresh, change password end to end |
| `SecurityRulesTest` | context | public and protected URLs, error bodies, trace ids |

35 tests at the time of writing, and the number should grow with every feature.

## Which kind to write

| Thing under test | Kind |
|------------------|------|
| A calculation, a value type, a rule with no I/O | unit, with real collaborators |
| A service where collaborators are irrelevant to the rule | unit, Mockito for the collaborators |
| A repository query or a migration | context |
| A controller's status, JSON and validation | context, MockMvc |
| A security rule (public, protected, role) | context, added to `SecurityRulesTest` |
| A module boundary | already covered by `ModularityTest` |

Default to a unit test. Reach for a context test when the wiring is the point.
A context test that only exercises a pure function is slow for no reason.

## Rules

- **Name the behaviour, not the method.** `refreshTokenIsRejectedAsAccessToken`,
  not `testParse`. Read the existing test names for the intended style.
- **AssertJ**, `assertThat(...)`, not JUnit's `Assertions` or Hamcrest. One
  matcher library per project.
- **Assert the outcome, not the implementation.** For an error path, assert the
  HTTP status **and** the `code` field, because the code is the client contract:

```java
mockMvc.perform(post("/v1/auth/register").contentType(APPLICATION_JSON).content(body))
       .andExpect(status().isConflict())
       .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
```

- **Tests clean up after themselves.** One PostgreSQL server serves the whole
  JVM, so classes share a database. Do not assume an empty table: create the data
  you need with unique values, and delete it in a `@BeforeEach` or `@AfterEach`.
- **No sleeping.** For anything time-dependent, inject a `Clock`, as
  `JwtTokenProvider` allows.
- **No conditional assertions.** An `if` in a test means two tests.
- **Constructor injection in tests too.** No `@Autowired` fields on a service
  under test.
- **Do not mock what you own and can construct.** Mock the collaborator that
  needs a database or a network, not `Money`.
- **A bug fix comes with a test that fails without the fix.** Otherwise the
  regression is only one refactor away.

## What must stay covered

These tests encode decisions, not just behaviour. Changing the code without
updating them is how a security property disappears quietly.

- `ModularityTest` — every module boundary rule. Never disable a case to make a
  build pass; fix the dependency.
- `SecurityRulesTest` — which URLs are public, that an unauthenticated call
  returns a JSON 401 and not an HTML error page, that a refresh token is refused
  as an access token, that every response carries a trace id, that malformed JSON
  is a 400 and not a 500.
- `JwtTokenProviderTest` — expired, forged, tampered and type-swapped tokens are
  all rejected.
- `AuthFlowIntegrationTest` — the full customer path against real PostgreSQL.

When you add a public endpoint, add its rule to `SecurityRulesTest` in the same
pull request.

## Test configuration

Context tests run with `@ActiveProfiles("test")` and get their datasource from
`PostgresIntegrationTest` through `@DynamicPropertySource`. Do not point a test
at a local or shared database: it would pass or fail depending on the machine.

## Coverage

There is no coverage gate. Judge a change by whether its behaviour and its
failure modes are tested, not by a percentage. A module with 90% coverage and no
test for its error responses is undertested.

## Not built yet

- No load or performance test, so the ~3,000 requests/second target is a sizing
  assumption and not a measured result.
- No contract test against a client.
- The embedded PostgreSQL has **no PostGIS extension**, so the first migration or
  query that needs PostGIS cannot be tested as things stand — see
  [ADR-0008](../architecture/adr/0008-real-postgresql-in-tests.md).
