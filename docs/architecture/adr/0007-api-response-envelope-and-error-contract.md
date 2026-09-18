# ADR-0007: API response envelope and error contract

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

Several clients will consume this API: a customer web app, a vendor portal, an
admin console and mobile apps. They all need to tell apart "worked", "you sent
something wrong", "you are not allowed" and "we broke", and they need to react to
specific business outcomes such as a username already being taken.

HTTP status codes carry the category but not the reason. A 409 on registration
could be a duplicate username or a duplicate email, and a client that wants to
highlight the right form field needs to know which.

Spring MVC also produces its own errors before any controller runs: unparseable
JSON, an unknown path, a wrong HTTP method. Those must not come back in a
different shape, and must not leak stack traces or exception messages.

## Problem

What does an API response body look like, and how does a client identify a
specific business failure?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| Raw payloads: the resource on success, whatever Spring produces on error | least code; most REST-idiomatic | error shape varies by failure type; no place for a trace id; clients write per-endpoint parsing |
| RFC 7807 `application/problem+json` for errors, raw payloads for success | a standard, and tooling understands it | two shapes to handle; `type` URIs add ceremony; still needs a custom field for the trace id |
| One envelope for success and error, with a stable machine-readable `code` | one parser for every response; trace id always present; the code is a documented contract | nests the payload under `data`; not what REST purists expect; the envelope is mandatory everywhere |

## Decision

One envelope, `shared.web.ApiResponse`, for every response the API produces.

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

Success carries `data` instead of `code`. Validation failures add `errors`, a
field-to-message map, with `code` set to `VALIDATION_ERROR`. Null fields are
omitted (`@JsonInclude(NON_NULL)`).

Rules:

- **The HTTP status is the outcome.** `code` is the reason, and is what a client
  switches on.
- **`code` is a stable contract.** Renaming one is a breaking API change.
- **Business failures throw a subclass of `shared.exception.ApplicationException`**
  which carries both the status and the code: `ValidationException` 400,
  `UnauthorizedException` 401, `NotFoundException` 404, `ConflictException` 409.
- **`platform.web.GlobalExceptionHandler` extends Spring's
  `ResponseEntityExceptionHandler`**, so every standard MVC error keeps its
  correct status (400, 404, 405, 406, 415) and only the body is replaced.
- **Nothing internal is returned.** `server.error.include-message: never` and
  `include-stacktrace: never`; the catch-all handler logs the exception and
  returns `INTERNAL_ERROR` with "An unexpected error occurred".
- **Every response carries a trace id**, both in the body as `traceId` and in the
  `X-Request-Id` response header, set by `platform.web.RequestIdFilter`.

Codes in use today: `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `UNAUTHORIZED`,
`ACCESS_DENIED`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `INTERNAL_ERROR`,
`USERNAME_EXISTS`, `EMAIL_EXISTS`, `INCORRECT_PASSWORD`, `PASSWORD_UNCHANGED`.

## Reason

One shape means one client parser and no per-endpoint special cases, and it gives
every response a guaranteed place for the trace id. That last part is what makes
a customer's "it failed at 10:30" actionable: the id in their screenshot is the
id in the logs.

Extending `ResponseEntityExceptionHandler` rather than catching `Exception`
broadly is what keeps a malformed body a 400 and an unknown path a 404 while
still returning our body. The alternative, hand-rolling those handlers, gets the
statuses wrong in the cases nobody tests.

RFC 7807 was the close second. It lost because it only standardises errors, so we
would still have designed the success shape and still have added a trace id
field.

## Consequences

**Positive**

- One response parser per client, for success and failure.
- A trace id is always present, in the body and in a header.
- Business outcomes are machine-readable without parsing messages.
- Stack traces and exception text cannot reach a client through this path.

**Negative**

- Payloads are nested under `data`, which some generated clients and tools
  dislike.
- `success` duplicates information already in the status code.
- The set of codes is a contract that has to be maintained and documented, and it
  will drift from the code unless reviewers check it.
- Every endpoint must remember to return `ApiResponse`; nothing in the compiler
  enforces it.

**Revisit when**

- A public, third-party-facing API is offered, where `application/problem+json`
  compliance may be worth more than a single shape.
- Streaming or file-download endpoints are added. Those cannot be enveloped and
  are an explicit exception to this ADR.
