# ADR-0010: Explicit idempotency guard instead of a transparent filter

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

Clients retry. A mobile app loses its connection after the request left the
phone, a customer taps "Place order" twice, a load balancer times out a request
the server is still handling, and a payment provider redelivers a webhook. Every
one of those produces a second HTTP request that is indistinguishable from a
first one.

For most of the API that is harmless. Registration is already protected by a
unique constraint on username and email, a password change is naturally
idempotent, and reads have no effect. The operations where a duplicate is
expensive are the ones we have not built yet: creating an order, capturing a
payment, refunding, and reserving stock. Charging a customer twice is not a bug
we can fix with an apology.

Redis is already configured and running (`spring.data.redis`), used for caching
today. HTTP responses are a single envelope, `ApiResponse`
([ADR-0007](0007-api-response-envelope-and-error-contract.md)), so a stored
response is one well-known JSON shape.

## Problem

How does a client safely retry a write, and where does that logic live?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| Nothing: rely on database constraints per case | no new infrastructure; constraints are the strongest guarantee available | only works where a natural unique key exists; a second payment capture has no such key; a retry gets a 500 from a constraint violation instead of the original answer |
| A servlet filter or interceptor that applies to every write endpoint, keyed on an `Idempotency-Key` header | clients get one uniform rule; nothing to remember per use case | caches responses nobody asked to cache, including ones that should never be replayed; the filter has to guess what is safe; every write endpoint pays a Redis round trip; where the guarantee actually holds becomes invisible in the code |
| An explicit `IdempotencyGuard` that a use case calls, backed by Redis | the guarantee is visible at the call site and testable; only the operations that need it pay for it; the service decides what a replay returns | each use case must opt in, and one that forgets is silently unprotected; two mechanisms to understand (constraints and the guard) |
| A database table of processed keys instead of Redis | transactional with the business write, so no window between the two | a hot, write-heavy table plus a cleanup job; the reservation marker becomes a row that must be cleaned up after a crash |

## Decision

An explicit guard, `platform/idempotency/IdempotencyGuard`, that a use case calls
with a caller-supplied `Idempotency-Key`. Backed by Redis:

- The key is the client's `Idempotency-Key` header, scoped by operation and
  caller.
- Before the work runs, the guard writes a **reservation marker** for that key.
  If the marker already exists and no response is stored yet, the original
  request is still in flight and the retry is rejected rather than run in
  parallel.
- On success the serialized `ApiResponse` JSON is stored under the key.
- A retry with the same key returns the stored response instead of doing the work
  again.
- TTL is 24 hours, after which a repeat is treated as a new request.

There is no transparent filter and no interceptor. An endpoint is idempotent
because its use case calls the guard, and for no other reason.

Nothing uses it yet. The first callers will be `order` and `payment`.

## Reason

The set of operations that genuinely need replay protection is small and known:
payment and order style writes. A transparent layer would apply the mechanism to
every write in the application to serve those few, and in doing so would store
responses for operations where replaying a response is wrong — anything whose
correct answer depends on current state rather than on the request. It also hides
the semantics: reading a controller would not tell you whether a retry is safe,
which is precisely the question a reviewer of a payment flow needs answered.

An explicit call keeps that question answerable by reading the use case, and it
keeps the cost where the benefit is.

Redis over a table because the data is short-lived by design and the reservation
marker wants a TTL, which is Redis' native behaviour rather than a cleanup job we
would have to write and monitor. The window between the Redis write and the
database commit is real; where that window is unacceptable, the operation also
needs a natural unique key in the database, and the guard is the fast path rather
than the only guarantee.

## Consequences

**Positive**

- Only the operations that need replay protection carry it, and it is visible in
  their code.
- A retry returns the original answer, not a constraint violation or a second
  charge.
- The reservation marker makes concurrent duplicates fail fast instead of
  running twice.
- No cache of responses nobody asked to cache, and no per-request Redis call on
  writes that do not need one.

**Negative**

- Opt-in means it can be forgotten. A new payment or order use case without a
  guard call looks fine and is not protected. This belongs on the
  [code review checklist](../../engineering/code-review-checklist.md).
- Redis becomes load-bearing for those operations, not just a cache. A Redis
  outage must fail the operation rather than silently skip the check, and
  readiness deliberately excludes Redis
  ([deployment](../../development/deployment.md)), so that failure mode has to be
  handled in the use case.
- The client has to send a key and reuse it on retry. That is an API contract we
  have to document per endpoint.
- Two mechanisms coexist: unique constraints where a natural key exists, the
  guard where none does.

**Revisit when**

- **`payment` or `order` is built.** Neither may ship without the guard on its
  write use cases. That is the trigger, not a suggestion.
- A webhook receiver arrives. Provider redeliveries are the same problem with a
  provider-supplied key, and the guard should take that key directly.
- The Redis-to-commit window causes a real incident, at which point move the key
  into the database transaction for that operation.
