# Coding standard

Rules a reviewer can check. Where a rule has an exception, it is named.

## Formatting

`.editorconfig` is the source of truth; configure your IDE to use it.

| Setting | Value |
|---------|-------|
| Encoding | UTF-8 |
| Line endings | LF (CRLF only for `*.cmd`) |
| Java indent | 4 spaces |
| YAML, JSON indent | 2 spaces |
| Final newline | required |
| Trailing whitespace | trimmed (not in `*.md`) |

Keep lines around 120 characters. Do not reformat code you did not change: a
diff should show the change, not the formatter.

## Packages and placement

Follow the module layout in
[module-architecture.md](../architecture/module-architecture.md). Before adding a
class, decide which package it belongs in:

| Class kind | Package |
|------------|---------|
| Controller | `modules/<name>/api` |
| Request or response record | `modules/<name>/api/dto` |
| Use-case service | `modules/<name>/application` |
| Entity, repository interface, domain rule | `modules/<name>/domain` |
| Event, enum or interface another module uses | `modules/<name>/contract` |
| Adapter to an external system | `modules/<name>/infrastructure` |
| Entity to response mapping | `modules/<name>/mapper` |

Never import another module's `api`, `application`, `domain`,
`infrastructure` or `mapper` package. `ModularityTest` fails the build if you do.

## Visibility

Default to package-private. Make a type `public` only when something outside its
package needs it.

- Controllers: package-private (`class AuthController`, not `public class`).
  Spring does not need them public.
- Services: package-private class with a package-private constructor unless a
  `contract` interface exposes them.
- DTO records in `api/dto`: public, because Jackson and springdoc read them.
- Everything in `contract`: public by definition.

## Naming

| Thing | Pattern | Example |
|-------|---------|---------|
| Use-case service | verb + noun + `Service` | `RegisterCustomerService`, `LoginService` |
| Controller | resource + `Controller` | `AuthController` |
| Request record | action + `Request` | `RegisterRequest`, `ChangePasswordRequest` |
| Response record | resource + `Response` | `UserResponse`, `AuthTokenResponse` |
| Event record | noun + past-tense verb + `Event` | `UserRegisteredEvent` |
| Configuration properties | area + `Properties` | `JwtProperties`, `CorsProperties` |
| Test | class under test + `Test` | `JwtTokenProviderTest` |

One use case per service class. A service named `UserService` with eight
unrelated methods is a review comment, not a style preference.

## Dependency injection

Constructor injection only. No `@Autowired` on fields, no setter injection, no
field injection in tests.

```java
class AuthController {
    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }
}
```

Fields are `private final`. The one accepted `@Autowired` in this codebase is on
a constructor that exists alongside a package-private test constructor, as in
`JwtTokenProvider(JwtProperties, Clock)` — that pattern is allowed when it is
what makes a class testable without Spring.

## Records and immutability

- Request and response DTOs are `record`s. Never entities, never mutable classes.
- Events are `record`s.
- Value types are `record`s: see `shared.money.Money`.
- Validate inside a record's compact constructor when the type has an invariant,
  as `JwtProperties` does for the minimum secret length. Failing at startup beats
  failing on the first request.

## Lombok

Allowed: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@Slf4j`.

Forbidden: `@Data`, `@EqualsAndHashCode` and `@ToString` on entities. Generated
`equals`/`hashCode` over all fields breaks JPA entities — it triggers lazy
loading and misbehaves in hash-based collections — and a generated `toString` on
`User` would log the password hash. `BaseEntity` documents this.

Do not use `@Builder` on entities. Do not use `@SneakyThrows`.

## Types

- **Money:** `shared.money.Money` only. Never `double`, `float` or a bare
  `BigDecimal`. Stored as `BIGINT` minor units plus a currency code.
- **Time:** `Instant` in Java, `TIMESTAMPTZ` in the database, UTC throughout.
  Never `Date`, `Calendar` or `LocalDateTime` for a point in time.
- **Identifiers:** `Long id` stays inside the module and the database; a `UUID`
  `publicId` is what appears in URLs, responses and tokens. A controller or
  response record that exposes the numeric id is a bug.
- **Optional:** fine as a return type, as `UserRepository.findByPublicId` does.
  Not as a field or a parameter.

## Exceptions

Throw a subclass of `shared.exception.ApplicationException` for anything a client
caused:

| Exception | Status | Use for |
|-----------|--------|---------|
| `ValidationException` | 400 | well-formed request, broken business rule |
| `UnauthorizedException` | 401 | missing, wrong or expired credentials |
| `NotFoundException` | 404 | resource does not exist, or the caller may not know it does |
| `ConflictException` | 409 | conflicts with existing data, such as a duplicate username |

Each carries a stable error code that clients switch on. Pass a specific one
where the generic default is not enough:

```java
throw new ConflictException("Username already exists", "USERNAME_EXISTS");
```

Never catch an exception to return `null`. Never catch `Exception` in a service;
`GlobalExceptionHandler` turns anything unexpected into a 500. Never put internal
detail in a message that reaches a client.

## Transactions and persistence

- `@Transactional` goes on the service method, never on a controller or a
  repository.
- Use `@Transactional(readOnly = true)` for reads.
- `open-in-view` is off. Fetch what you need inside the transaction; touching a
  lazy association from a controller or mapper throws.
- Inside a transaction, a setter on a managed entity **is** the update. Do not
  call `save` again for a dirty-checked change.
- Never write a query with string concatenation. Use derived queries or `@Query`
  with named parameters.

## Logging

`@Slf4j`, and log through `log`, never `System.out`.

| Level | Use |
|-------|-----|
| `error` | unexpected failure, with the exception as the last argument |
| `warn` | something a human should look at |
| `info` | business events worth a line: registration, password change |
| `debug` | expected rejections: a bad token, a failed login |

Use parameterised messages (`log.info("Registered user {}", id)`), never string
concatenation.

**Never log:** passwords, password hashes, tokens, secrets, full email addresses
or a whole request body. Log a `publicId` instead of a name or email. Every line
already carries the trace id via the MDC, so there is no need to add it by hand.

## Static analysis

SpotBugs runs as part of `./mvnw verify` (`spotbugs-maven-plugin`, effort `Max`,
threshold `Medium`, main sources only). It fails the build on likely bugs such as
a null dereference or a leaked resource, so a SpotBugs finding is not advisory.

Fix the finding. If it is genuinely a false positive, add a narrow entry to
`config/spotbugs-exclude.xml` with a comment saying why, and mention it in the
pull request. Do not widen an existing exclusion to make a new finding go away.

## Comments

Write a comment for *why*, not *what*. A Javadoc on a class or a public method
should say what it is for and what a caller must know — see `AuthenticatedUser`
or `RequestIdFilter` for the intended level. Delete commented-out code rather
than leaving it.

## See also

- [API standard](api-standard.md)
- [Database standard](database-standard.md)
- [Security standard](security-standard.md)
- [Testing standard](testing-standard.md)
- [Code review checklist](code-review-checklist.md)
