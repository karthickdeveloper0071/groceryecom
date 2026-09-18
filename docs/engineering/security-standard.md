# Security standard

What is implemented, the rules that keep it that way, and the gaps. The model is
stateless JWT ([ADR-0005](../architecture/adr/0005-stateless-jwt-authentication.md)).

## Tokens

| Property | Value |
|----------|-------|
| Algorithm | HS512, symmetric |
| Access token TTL | 15 minutes |
| Refresh token TTL | 7 days |
| Subject | the user's `public_id` UUID |
| Access claims | `token_type=access`, `username`, `roles`, `jti`, `iat_ms` |
| Refresh claims | `token_type=refresh`, `jti` |
| Transport | `Authorization: Bearer <token>` |

Rules:

- Issue and verify tokens only through `platform.security.JwtTokenProvider`.
  Never call `Jwts` directly from a module.
- The `token_type` claim is checked on every parse, so a refresh token cannot be
  used as an access token. `JwtTokenProviderTest` covers swapped, expired,
  forged and tampered tokens; keep that coverage when the class changes.
- Never put anything sensitive in a token. Claims are signed, not encrypted —
  anyone holding a token can read them.
- Do not lengthen the access-token TTL to work around a client problem. A short
  TTL bounds the damage from a stolen token even with revocation in place.

## Revocation

`platform.security.token.TokenRegistry` holds the state that makes stateless
tokens revocable. `JwtAuthenticationFilter` checks it on every authenticated
request.

| Event | Effect |
|-------|--------|
| `POST /v1/auth/logout` | this access token is refused for the rest of its life; the refresh token sent with it is consumed. Other devices stay logged in. |
| `POST /v1/auth/logout-all` | every token of the user issued before now is refused |
| Password change | same as logout-all, so a leaked token stops working |
| Refresh token used twice | the token leaked or a client is buggy: every session of that user is revoked and `REFRESH_TOKEN_REUSED` is written to the audit log |

Rules:

- Every refresh token works **once**. Using it returns a new pair (rotation).
- Tokens carry an `iat_ms` claim, because the standard `iat` is only accurate to
  the second and revocation needs to tell "issued just before the logout" from
  "issued by logging straight back in".
- The store is chosen by `app.security.tokens.store`: `redis` (default, shared by
  every instance) or `memory` (single instance, tests only).
- Failure policy is deliberate and asymmetric: the access-token check **fails
  open** if Redis is unreachable, because refusing every request would turn a
  cache outage into a full outage and the exposure is bounded by the 15-minute
  TTL. Refresh consumption **fails closed**, because a refresh token buys 7 days
  of access. Do not "simplify" this to one policy without re-reading this
  paragraph.
- Covered by `SessionSecurityIntegrationTest` end to end against PostgreSQL.

## Rate limiting

`platform.ratelimit` limits the public authentication endpoints. The filter runs
before Spring Security, so a flood of guesses never reaches BCrypt.

| Limit | Default | Key |
|-------|---------|-----|
| `app.security.rate-limit.login` | 20 / minute | client address |
| `app.security.rate-limit.login-per-account` | 10 / 5 minutes | the username or email being tried |
| `app.security.rate-limit.register` | 5 / 10 minutes | client address |
| `app.security.rate-limit.refresh-token` | 60 / minute | client address |

- Two layers on purpose: the per-address limit stops one machine, the per-account
  limit stops a botnet grinding down one account from many addresses.
- Over the limit returns 429 with code `RATE_LIMITED` and a `Retry-After` header.
- The counter **fails open** when Redis is down: a limiter that cannot count must
  not become an outage of login. The failure is logged.
- A fixed window per key, so a caller can send up to twice the limit across a
  window boundary. Accepted trade; a sliding window costs more than it is worth here.
- Client addresses are only trustworthy because `X-Forwarded-For` is honoured
  solely from configured proxies (see the transport rules above). Do not switch
  `forward-headers-strategy` back to `framework`.

## The signing secret

- Configured as `app.security.jwt.secret`, set by `JWT_SECRET`.
- `JwtProperties` rejects a secret shorter than 64 bytes in its constructor, so
  the application refuses to start rather than failing at the first login.
- The default in `application.yml` is a visible local-development value. It must
  never be used in any shared environment; `docker-compose.yml` deliberately has
  no default for the `app` profile
  (`JWT_SECRET: ${JWT_SECRET:?set JWT_SECRET in .env}`).
- A different secret per environment, from a secrets manager, never in Git.
  Generate one with `openssl rand -base64 64 | tr -d '\n'`.
- Rotating the secret invalidates every issued token of every user at once, so it
  is a last resort. For one user, use logout-all; the registry handles that
  without disturbing anyone else.

## URL rules

`platform.security.SecurityConfig` is the single place where a path becomes
public. Matchers are relative to the `/api` context path.

| Public | Paths |
|--------|-------|
| POST | `/v1/auth/register`, `/v1/auth/login`, `/v1/auth/refresh-token` |
| GET | `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` |
| any | `/error` |

Everything else is `authenticated()`. Notice that the public entries are **method
specific**: a POST to a public GET path is not public.

`GET /actuator/prometheus` and `GET /actuator/metrics` are a middle case: they are
permitted **only** when the request comes from a loopback or private address
(127.0.0.1, ::1, 10/8, 172.16/12, 192.168/16), because metrics describe the
system and a scraper cannot hold a user token. From any other address they are
`authenticated()` like everything else. See the
[observability standard](observability-standard.md).

**Adding a path to either list is a security change.** It needs a reason in the
pull request description and a reviewer who agrees. `SecurityRulesTest` asserts
these rules; extend it when you change them.

Method security is enabled (`@EnableMethodSecurity`), so role checks go on the
service or controller method with `@PreAuthorize`. Prefer that over adding more
URL-level rules: the check then lives next to the code it protects.

## Passwords

- Hashed with Spring's delegating encoder
  (`PasswordEncoderFactories.createDelegatingPasswordEncoder()`), stored with a
  `{bcrypt}` prefix so the algorithm can be upgraded without invalidating
  existing passwords.
- Minimum 8 characters, maximum 72 — BCrypt only uses the first 72 bytes, so
  allowing more would silently ignore the rest.
- Compare with `passwordEncoder.matches`. Never compare hashes as strings.
- Never log, return or store a password or a hash. `User` uses `@Getter`/`@Setter`
  and not `@Data` partly so no generated `toString` can print `passwordHash`.

Two deliberate behaviours in the login path, which must survive refactoring:

**Constant-ish response time.** When no account matches, the code still runs a
password comparison against a dummy hash:

```java
if (found.isEmpty()) {
    passwordEncoder.matches(request.password(), dummyPasswordHash);
    throw new UnauthorizedException(INVALID_CREDENTIALS);
}
```

Without it, a missing account returns noticeably faster than a wrong password and
an attacker can enumerate accounts by timing.

**Password before account state.** The password is checked before `is_active` and
`is_deleted`, so "this account is disabled" is only revealed to someone who
already knows the password. Reordering these two checks leaks account existence.

Both failures return the same message, `Invalid username or password`, with code
`UNAUTHORIZED`.

## Registration

`POST /api/v1/auth/register` always creates a `CUSTOMER`. `RegisterRequest` has
no role field on purpose — an accepted role parameter is a privilege-escalation
endpoint. Vendor and admin accounts come from vendor onboarding and the admin
console.

## Identifying the caller

- Inject `@AuthenticationPrincipal AuthenticatedUser`. Never trust a user id
  taken from a path, a body or a query parameter as "the current user".
- `AuthenticatedUser.id()` is the public UUID. The numeric id never leaves the
  database.
- `changePassword` takes the id from the principal, not the request. Keep that
  shape for every "act on myself" endpoint.

## What is never returned

- Stack traces and exception messages: `server.error.include-message: never`,
  `include-stacktrace: never`, and the catch-all handler returns
  `INTERNAL_ERROR` with a fixed message.
- Whether an account exists, from login or password reset.
- Internal ids, password hashes, or any field the client has no use for.
- Actuator details: only `health` and `info` are exposed and
  `management.endpoint.health.show-details=when-authorized`.

## Transport and browser rules

- CSRF, form login, HTTP basic and Spring's logout endpoint are disabled. None
  apply to a bearer-token API, and leaving them on adds attack surface.
- Sessions are `STATELESS`; nothing is stored server-side per user.
- CORS origins come from `app.security.cors.allowed-origin-patterns`
  (`CORS_ALLOWED_ORIGINS`, comma-separated). This is a multi-vendor platform with
  many frontends — the customer app, the admin console and a storefront per
  vendor — so entries are **patterns**: `https://*.groceryecom.com` matches
  `https://vendor-42.groceryecom.com`, and a new vendor subdomain needs no
  redeploy. `*` replaces exactly one host label, so
  `https://groceryecom.com.evil.example` does not match.
  Never `*` alone in a shared environment: any website could then call the API
  with a user's token. A vendor on its own domain (not our subdomain) must be
  listed explicitly; when that list stops being manageable, replace the
  properties-based source with a `CorsConfigurationSource` that reads verified
  vendor domains from the vendor module and caches them in Redis.
- Allowed headers are `Authorization`, `Content-Type`, `Accept`,
  `Accept-Language`, `X-Request-Id`, `Idempotency-Key`; `X-Request-Id` is
  exposed so a frontend can show the trace id in a bug report.
  `allowCredentials` is false because tokens travel in a header, not a cookie.
- Preflight (`OPTIONS`) is answered by the CORS filter before authorization, so
  a browser check never needs a token. `CorsRulesTest` covers allowed
  subdomains, an explicitly listed vendor domain, look-alike hosts and unknown
  sites.
- TLS is terminated at the load balancer.
  `server.forward-headers-strategy=framework` makes client IPs and redirects
  correct behind it.

## Secrets

- Nothing secret in Git. `.env` is git-ignored; `.env.example` documents the
  variables with empty or local-only values.
- Every secret is an environment variable with no production default.
- The Docker image runs as the non-root user `app`.
- CI runs a Trivy scan in `secret` and `vuln` modes and fails on HIGH or
  CRITICAL, so a committed credential or a vulnerable dependency does not reach
  `main` unnoticed.

## Not built yet

These are real gaps. Do not write code, or documentation, that assumes otherwise.

| Gap | Consequence | Likely first step |
|-----|-------------|-------------------|
| **No database-backed audit trail** | security events go to the `audit` logger, so there is no queryable history and no retention guarantee | an append-only table written in the same transaction as the change |
| **No admin-initiated password reset or account disable API** | an operator cannot lock an account out from outside the database | an admin endpoint that sets `is_active` and calls logout-all |
| **No password reset flow** | a user who forgets a password cannot recover it | a single-use, time-limited token sent by email, once `notification` exists |
| **Email and phone are never verified** | `email_verified` and `phone_verified` exist on `users` and are always false | verification links, once `notification` exists |

## See also

- [API standard](api-standard.md) for error codes and the response envelope
- [Testing standard](testing-standard.md) for what the security tests cover
- [Code review checklist](code-review-checklist.md)
