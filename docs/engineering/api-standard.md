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

| Method | Path | Auth | Success status |
|--------|------|------|----------------|
| POST | `/api/v1/auth/register` | public | 201 |
| POST | `/api/v1/auth/login` | public | 200 |
| POST | `/api/v1/auth/refresh-token` | public | 200 |
| GET | `/api/v1/auth/me` | bearer token | 200 |
| POST | `/api/v1/auth/change-password` | bearer token | 200 (ends every session) |
| POST | `/api/v1/auth/logout` | bearer token | 200 (this device; send the refresh token in the body) |
| POST | `/api/v1/auth/logout-all` | bearer token | 200 (every device) |

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
| `USERNAME_EXISTS` | 409 | registration, username taken |
| `EMAIL_EXISTS` | 409 | registration, email taken |
| `INCORRECT_PASSWORD` | 400 | change password, old password wrong |
| `PASSWORD_UNCHANGED` | 400 | change password, new password equals old |
| `ACCOUNT_EXISTS` | 409 | registration lost a race on a unique constraint |
| `CONCURRENT_MODIFICATION` | 409 | optimistic lock failure; reload and retry |
| `IDEMPOTENT_REQUEST_IN_PROGRESS` | 409 | same `Idempotency-Key` is still being processed |
| `RATE_LIMITED` | 429 | too many requests; a `Retry-After` header says when to retry |

Adding a business code: define it where the exception is thrown, add a row to
this table in the same pull request, and use `SCREAMING_SNAKE_CASE`.

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

## Pagination (not built yet)

No endpoint returns a collection yet, so there is no pagination convention in
code. When the first one lands, agree the shape once, document it here, and use
it everywhere; do not invent it per endpoint.
