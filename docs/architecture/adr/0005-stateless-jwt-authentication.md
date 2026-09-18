# ADR-0005: Stateless JWT authentication

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

The API serves browser and mobile clients and runs as 3-8 interchangeable
instances behind a load balancer. Any instance may serve any request, and
instances come and go on deploy. Peak load is ~20,000 concurrent users and
~3,000 requests/second, so authentication runs on the hot path of every request.

There is no external identity provider in stage 1: the `identity` module owns
accounts and passwords.

## Problem

How does a request prove who it belongs to, without requiring the load balancer
to send a user to the same instance every time?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| Server-side sessions in memory | simplest; logout works immediately | breaks with several instances unless the load balancer pins users to one; a deploy logs everyone out |
| Server-side sessions in Redis (Spring Session) | works across instances; logout and revocation work; session data can be updated centrally | Redis becomes required for every request, so a Redis outage is a total outage; adds a network hop to the hot path; readiness would have to include Redis |
| Stateless signed JWT | no shared state; any instance can verify locally; no infrastructure on the auth path | a token cannot be revoked before it expires, so real logout needs a deny-list; roles in the token are stale until it expires; the signing key is a single high-value secret |
| OAuth2 / OIDC with an external provider | offloads password handling; standard flows; social login | an external dependency and cost for a feature set we do not need yet; still needs local accounts for vendor staff |

## Decision

Stateless JWTs signed with HS512, issued and verified by
`platform.security.JwtTokenProvider`.

| Property | Value |
|----------|-------|
| Algorithm | HS512 (symmetric) |
| Access token TTL | 15 minutes (`app.security.jwt.access-token-ttl`) |
| Refresh token TTL | 7 days (`app.security.jwt.refresh-token-ttl`) |
| Subject | the user's `public_id` UUID, never the database id |
| Access token claims | `token_type=access`, `username`, `roles` |
| Refresh token claims | `token_type=refresh` only |
| Transport | `Authorization: Bearer <token>` header, never a cookie |

Supporting rules:

- The `token_type` claim is checked on parse, so a refresh token cannot be used
  as an access token or the other way round.
- `JwtProperties` rejects a secret shorter than 64 bytes in its constructor, so
  the application refuses to start rather than failing at the first login.
- `SessionCreationPolicy.STATELESS`; CSRF, form login, HTTP basic and Spring's
  logout endpoint are all disabled, because none of them apply to a bearer-token
  API.
- Everything requires authentication except `POST /api/v1/auth/register`,
  `/login`, `/refresh-token`, `GET /api/actuator/health*`, `/api/actuator/info`
  and the OpenAPI endpoints.

## Reason

Stateless verification keeps the auth path free of infrastructure: an instance
needs the signing key and nothing else, which is what lets readiness depend on
the database alone and survive a Redis outage. A 15-minute access token bounds
the damage from a stolen or stale token without making clients log in every 15
minutes, because the refresh token covers a week.

HS512 rather than RS256 because one service both issues and verifies tokens.
There is no third party that needs to verify without being able to sign. If that
changes, move to RS256 and publish a JWKS.

## Consequences

**Positive**

- No shared session store; any instance serves any request; a deploy does not
  log anyone out.
- Redis or RabbitMQ being down does not break authentication.
- Verification is a local signature check, so it costs nothing on the hot path.
- Exposing only `public_id` in the token subject means the numeric primary key
  never leaves the database.

**Negative**

- **No revocation.** An issued access token stays valid for up to 15 minutes even
  after a password change, a role change or an account being disabled. There is
  no real logout: clients discard the token, the server cannot.
- Role changes take effect on the next token issue, not immediately.
- The signing secret is a single high-value credential. A leak lets anyone mint
  tokens, and the only remedy is rotating the secret, which invalidates every
  token at once.
- Every environment needs its own `JWT_SECRET`, which is real operational work.

**Revisit when**

- Real logout, "sign out all devices" or immediate account disabling becomes a
  requirement. The next step is a Redis deny-list of refresh-token ids, checked
  only on refresh, so the hot path stays stateless.
- Another service needs to verify tokens without being able to issue them, which
  is the point to move to RS256 plus JWKS.
- Social login or single sign-on is needed, which means OIDC.

Two related gaps are tracked in the
[security standard](../../engineering/security-standard.md) rather than here:
there is **no rate limiting** on login or registration, and **no audit log**.
