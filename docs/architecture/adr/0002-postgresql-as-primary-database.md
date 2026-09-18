# ADR-0002: PostgreSQL as the primary database

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

The data is relational and the invariants are financial. An order has per-vendor
sub-orders and items; a payment has a ledger and refunds; inventory has stock
levels and reservations. Money must never be double-spent or lost, so we need
real transactions across several tables. Delivery will need geographic queries
(delivery zones, "is this address in range"). Stage 1 volume is ~35,000
orders/day and ~3,000 requests/second, most of it reads.

## Problem

Which database stores the marketplace's primary data?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| PostgreSQL | ACID transactions across tables; strong constraints (CHECK, unique, foreign keys); PostGIS for delivery zones; JSONB where a column set is genuinely open; partitioning and read replicas; Flyway and Spring Data JPA support are first-class | one primary means writes have a ceiling; schema changes need migrations |
| MySQL | widely known; good replication | weaker constraint and type support; no PostGIS equivalent; poorer JSON and partitioning story |
| MongoDB | flexible documents; easy horizontal scaling | multi-document transactions are awkward and easy to get wrong; the money invariants here are exactly what it is worst at; joins move into application code |
| PostgreSQL plus a document store for catalog | catalog documents fit a document model | two stores to keep consistent, for a benefit we do not need at 100 vendors |

## Decision

PostgreSQL 17 is the single primary datastore. We run the
`postgis/postgis:17-3.5` image locally so the PostGIS extension is available when
`delivery` needs it. Flyway owns the schema; Hibernate runs with
`ddl-auto=validate`. Redis is a cache, and will hold carts later, but is never
the source of truth.

Stage 1 topology: one primary plus one read replica. No sharding.

## Reason

Every hard requirement in this domain is a relational-integrity requirement, and
PostgreSQL is the option that enforces those in the database rather than in
application code. PostGIS removes the need for a second store for delivery zones.
Partitioning gives a growth path for the high-volume tables without changing the
application, which is why the database rules require `created_at` in the keys of
high-volume tables from the start.

35,000 orders/day is under one write per second averaged, and a few dozen at
peak. That is nowhere near a single primary's limit. Read traffic is the part
that grows, and a read replica handles it.

## Consequences

**Positive**

- Multi-table invariants are enforced by constraints, not by hope.
- One transaction covers an order, its items, its payment row and its outbox
  event, see [ADR-0006](0006-transactional-outbox-for-module-events.md).
- `ddl-auto=validate` turns an entity/schema mismatch into a startup failure
  instead of a runtime error under load.
- PostGIS is already available for delivery zones.

**Negative**

- Writes have a single-node ceiling.
- Every schema change is a migration file and must stay backward compatible with
  the running version.
- Connection count is a real budget: `DB_POOL_SIZE` (default 20) times the
  instance count must stay under the server limit.

**Revisit when**

- Write throughput or table size stops fitting one primary. The first step is
  partitioning `orders`, `order_items` and the payment tables by month, which the
  key design already allows, not sharding.
- Catalog search needs ranking and typo tolerance that SQL cannot express. That
  is a search index alongside PostgreSQL, not a replacement for it.
