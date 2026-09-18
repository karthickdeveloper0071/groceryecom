# API standard

The rules every HTTP endpoint in this project follows. The reasoning behind the
envelope is in
[ADR-0007](../architecture/adr/0007-api-response-envelope-and-error-contract.md).

## URLs

- Context path is `/api` (`server.servlet.context-path`). Request matchers in
  `SecurityConfig` and `@RequestMapping` values are written **without** it:
  `@RequestMapping("/v1/auth")` serves `/api/v1/auth`.
- Every endpoint is versioned: `/api/v1/...`. A breaking change to a response
  means `/api/v2/...`, not a silent edit.
- Plural, lower-case, hyphenated resource paths: `/v1/auth/refresh-token`,
  `/v1/vendors/{vendorId}/products`.
- Audience prefixes for future modules: `/api/v1/vendor/...` for the vendor
  portal, `/api/v1/admin/...` for the admin console.
- Path variables use the resource's **public UUID**, never the numeric id.

## Endpoints today

No business endpoint exists yet. The only paths the application serves are the
operational ones:

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/actuator/health` | public | is it alive, and should it get traffic |
| GET | `/api/actuator/prometheus` | private network only | metrics for scraping |
| GET | `/api/swagger-ui.html` | public | the API documentation |

Add a row here for every endpoint you build. Two kinds of authorisation will appear
in it, and they are not interchangeable: a **role** (`@PreAuthorize("hasRole(...)")`)
answers "what kind of person is this", while a **membership** check inside the service
answers "may this person touch *this* record". A role cannot express the second, and
using one where the other belongs is how one customer reads another customer's data.

OpenAPI: `/api/swagger-ui.html` and `/api/v3/api-docs`.

## Methods and statuses

| Method | Use | Success |
|--------|-----|---------|
| GET | read, never changes state | 200 |
| POST | create, or an action that is not a plain update | 201 for a create, 200 otherwise |
| PUT | full replace | 200 |
| PATCH | partial update | 200 |
| DELETE | remove | 204, or 200 with a body |

Set a non-default status with `@ResponseStatus`, as `register` does with
`HttpStatus.CREATED`.

## Response envelope

Every response is `shared.web.ApiResponse`. Null fields are omitted.

Success:

```json
{
  "success": true,
  "data": { "id": "9b1c...", "username": "asha", "role": "CUSTOMER" },
  "message": "Registered",
  "timestamp": "2026-09-18T10:30:00Z",
  "traceId": "6f1c..."
}
```

Error:

```json
{
  "success": false,
  "code": "USERNAME_EXISTS",
  "message": "Username already exists",
  "timestamp": "2026-09-18T10:30:00Z",
  "traceId": "6f1c..."
}
```

Validation failure — adds `errors`, a field-to-message map:

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errors": { "email": "must be a well-formed email address", "password": "size must be between 8 and 72" },
  "timestamp": "2026-09-18T10:30:00Z",
  "traceId": "6f1c..."
}
```

Build it with the factory methods, never with the constructor:

```java
return ApiResponse.ok(authService.login(request));            // data only
return ApiResponse.ok(response, "Registered");                // data plus message
return ApiResponse.error("USERNAME_EXISTS", "Username already exists");
```

Controllers return `ApiResponse<T>` directly. Return `ResponseEntity` only when
you need to set a header or compute the status at runtime.

The only accepted exception to the envelope is a non-JSON response body: a file
download or a stream. There are none today.

## Error codes

The HTTP status is the category; `code` is the reason a client switches on. A
code is a **stable contract** — renaming one is a breaking change and needs a new
API version.

| Code | Status | Raised by |
|------|--------|-----------|
| `VALIDATION_ERROR` | 400 | Bean Validation failure, or `ValidationException` with no specific code |
| `MALFORMED_REQUEST` | 400 | body missing or not valid JSON |
| `UNAUTHORIZED` | 401 | no or invalid token, bad credentials, disabled account |
| `ACCESS_DENIED` | 403 | authenticated but not allowed |
| `NOT_FOUND` | 404 | unknown resource or unknown path |
| `METHOD_NOT_ALLOWED` | 405 | wrong HTTP method for the path |
| `INTERNAL_ERROR` | 500 | anything unexpected |
| `CONCURRENT_MODIFICATION` | 409 | optimistic lock failure (`@Version` on `BaseEntity`); reload and retry |
| `RATE_LIMITED` | 429 | too many requests; a `Retry-After` header says when to retry |
| `SECRET_UNREADABLE` | 500 | a stored secret cannot be decrypted, usually after `SECRETS_MASTER_KEY` changed |

Those are the codes the platform itself produces; every business module adds its
own. Define a code where the exception is thrown, add a row to this table in the
same pull request, and use `SCREAMING_SNAKE_CASE`.

Two habits worth keeping when you do: make the **status** the category and the
**code** the reason, and pick the status by what the client should *do*. A plan
that expired is 402 and not 403, because one leads to a renew screen and the other
to an error page.

## Raising errors

Throw an `ApplicationException` subclass; never build an error response by hand
in a controller.

```java
if (userRepository.existsByUsername(username)) {
    throw new ConflictException("Username already exists", "USERNAME_EXISTS");
}
```

`platform.web.GlobalExceptionHandler` converts it. Because that class extends
Spring's `ResponseEntityExceptionHandler`, standard MVC failures keep their
correct status and only the body is replaced. Messages are written for a client:
no class names, no SQL, no stack traces.

## Request and response bodies

- Java `record`s in `modules/<name>/api/dto`. Never an entity, in either
  direction.
- Validate with Bean Validation on the record and `@Valid` on the parameter.
  A cross-field rule is an `@AssertTrue` method, as in `ChangePasswordRequest`:

```java
@JsonIgnore
@AssertTrue(message = "must match newPassword")
public boolean isConfirmPasswordMatching() {
    return Objects.equals(newPassword, confirmPassword);
}
```

- Field names are `camelCase`. Timestamps are ISO-8601 UTC (`Instant`), amounts
  are minor units plus a currency code, never a float.
- A response never contains a password, a hash, an internal id or a field the
  client has no use for. Build responses in the module's `mapper` package.
- `spring.jackson.default-property-inclusion=non_null`, so absent means null.

## Trace id

`platform.web.RequestIdFilter` runs first on every request. It reuses an inbound
`X-Request-Id` when it matches `[A-Za-z0-9._-]{1,64}`, otherwise generates a
UUID. The id is put in the SLF4J MDC under `traceId`, returned in the
`X-Request-Id` response header, and included in every body as `traceId`. The
console log pattern includes `[%X{traceId:-}]`.

Clients should send `X-Request-Id` when they have their own correlation id, and
show `traceId` on error screens so support can find the log lines.

## Authentication in a controller

```java
@GetMapping("/me")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
    return ApiResponse.ok(authService.getUser(user.id()));
}
```

- Inject `@AuthenticationPrincipal AuthenticatedUser`. Never read the
  `SecurityContextHolder` in a controller.
- Never accept a user id in a request body or path for "the current user" — take
  it from the principal, or a caller can act as someone else.
- Add `@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)` to every
  authenticated endpoint so Swagger UI sends the token.

## OpenAPI

Annotate every endpoint with `@Operation(summary = "...")` and every controller
with `@Tag`. The summary is one line in the imperative: "Register a customer
account". Public endpoints must also be listed in `SecurityConfig`, and adding
one there is a security review point — see the
[security standard](security-standard.md).

## Pagination

Every endpoint that returns a collection uses the same shape, and none invents its
own. Request parameters:

| Parameter | Default | Notes |
|-----------|---------|-------|
| `page` | `0` | Zero-based, like the database and every client library |
| `size` | `20` | **Capped at 100.** A caller does not decide how much work the database does; a larger value is clamped, not refused |

The response is `shared.web.PageResponse`, inside the usual envelope:

```json
{
  "success": true,
  "data": {
    "items": [ ... ],
    "page": 0,
    "size": 20,
    "totalItems": 137,
    "totalPages": 7,
    "hasNext": true
  },
  "timestamp": "2026-09-18T10:30:00Z",
  "traceId": "6f1c..."
}
```

`hasNext` is sent although a client could compute it: paging code that does
arithmetic to decide whether to enable a button is paging code with an off-by-one
in it.

Spring Data's own `Page` is never serialised to a client - its JSON is large, it
carries objects nobody uses, and its shape has changed between Spring versions.
Build the page with `PageResponse.of(page, Mapper::toResponse)` and the request
with `PageRequestParams.newestFirst(page, size)` (or `oldestFirst` for a work
queue: people are served in the order they arrived).
