# ADR-0011: Optimistic locking with @Version on BaseEntity

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

Several writes will race. Two customers add the last unit of a product to their
baskets at the same time. A vendor edits a product while an admin deactivates it.
Two instances handle a refund and a capture for the same payment. The application
runs as several stateless instances behind a load balancer, so "it is one process
so it cannot happen" is never true here.

JPA entities are the domain model ([ADR-0004](0004-jpa-entities-as-domain-model.md)),
and a write is a setter on a managed entity flushed by dirty checking. Hibernate
writes `UPDATE ... SET col = ?` for the fields that changed, using the primary
key in the `WHERE` clause. Two transactions that both read a row and both write
it therefore both succeed, and the second silently overwrites the first. That is
a lost update, and nothing in the current code would notice.

`shared/persistence/BaseEntity` is the single base class every entity extends, so
it is the one place where a column can be added to every table at once. Flyway
owns the schema and Hibernate runs `ddl-auto=validate`, so an entity field with
no matching column stops startup.

## Problem

How do we stop concurrent writes from silently overwriting each other?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| No locking; accept last-write-wins | no code, no column, no new failure mode for clients | lost updates on exactly the data that must not be lost: stock counts, payment ledger entries, order status. The bug is invisible — no error, just wrong data |
| `@Version` on `BaseEntity`, so every table gets a `version` column | one place to implement; every entity protected by default, including ones nobody thought about; no extra query and no lock held; Hibernate already includes the version in the `UPDATE ... WHERE` clause | every table carries a column most rows never contend on; every migration must add it; callers now see a 409 they have to handle; a long-lived edit form fails at save time rather than at open time |
| `@Version` only on the entities that need it | no unused columns | requires predicting which entities contend, and being right; adding it later to a busy table is a migration plus a code change under pressure; easy to forget on a new entity, which is the same silent bug as having no locking |
| Pessimistic `SELECT ... FOR UPDATE` per use case | the strongest guarantee; the correct tool for a read-modify-write that must not fail | holds a database lock for the length of the transaction, so it costs throughput and can deadlock; needs a deliberate query per case; wrong as a default for CRUD reads and writes |

## Decision

`shared/persistence/BaseEntity` carries a `@Version` field, so **every** table
gets a `version BIGINT NOT NULL DEFAULT 0` column and every update is checked
optimistically.

When the check fails, Hibernate throws
`ObjectOptimisticLockingFailureException`. `platform/web/GlobalExceptionHandler`
maps it to HTTP **409** with error code **`CONCURRENT_MODIFICATION`** in the
standard `ApiResponse` envelope
([ADR-0007](0007-api-response-envelope-and-error-contract.md)). Clients are
expected to reload and retry; the message says so and never leaks the exception
text.

Pessimistic locking stays available and is the right choice where a failed write
is not acceptable — stock reservation is the known case, and it will use
`SELECT ... FOR UPDATE` on top of this, not instead of it.

## Reason

The failure mode we are preventing is a silent one. Everything else on the list
is a trade about cost; lost updates produce wrong data with no error, on the rows
we care about most. That makes "on by default" worth more than "only where
needed", because the entity someone forgets is exactly the entity that gets it
wrong.

`BaseEntity` makes the default free to apply: one field, one column in each
migration, and Hibernate does the rest inside the `UPDATE` it was already going
to send. There is no extra round trip and no lock held between the read and the
write, which is what makes optimistic the right default for a multi-instance
stateless application.

Per-entity versioning fails for the reason it sounds attractive: it assumes we
can name the contended entities up front. Adding a version column to a table that
is already hot, after the first corruption report, is worse work under worse
conditions.

Pessimistic locking is not a competing default. It is heavier — a held lock, a
deliberate query, deadlock risk — and it is needed anyway for stock reservation,
where the correct behaviour is to wait rather than to tell the customer to try
again.

## Consequences

**Positive**

- Lost updates surface as a 409 instead of wrong data.
- One implementation for every entity, present and future, with no per-entity
  decision to get right.
- No locks held across a transaction, so throughput is unaffected in the common
  uncontended case.
- Clients get a specific, documented error code they can act on, rather than a
  generic 500.

**Negative**

- **Every new migration must include `version BIGINT NOT NULL DEFAULT 0`.** A
  missing column fails `ddl-auto=validate` at startup, so the mistake is caught,
  but it is a mistake that will happen. It belongs in the
  [database standard](../../engineering/database-standard.md) and on the
  [code review checklist](../../engineering/code-review-checklist.md).
- A `version` column on tables that will never see contention.
- Callers must handle 409. A client that ignores it appears to lose the user's
  edit, which is a worse experience than last-write-wins if nobody implements the
  retry.
- Retry logic has to live somewhere. A blind retry loop around a use case can
  make contention worse; prefer reloading and re-deciding.
- Bulk `UPDATE` statements written as JPQL or native SQL bypass the check
  entirely. Those need a reviewer's attention.

**Revisit when**

- A use case cannot tolerate a 409 — stock reservation and the payment ledger are
  the expected ones. Add pessimistic locking for that query specifically, and
  document it in the module, rather than removing the version column.
- Contention on a specific entity is high enough that 409s become common in the
  metrics. That is a signal the operation should be modelled differently, for
  example as an append-only ledger instead of a mutable balance.
