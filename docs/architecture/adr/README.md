# Architecture decision records

## What an ADR is

An ADR records one architectural decision: what we had to decide, what we chose,
why, and what we gave up. It is written once, at the time of the decision, and
then left alone. When a decision changes, write a new ADR and mark the old one
`Superseded by ADR-000N` — never rewrite history, because the point of an ADR is
to explain a choice to someone reading the code two years later.

Write an ADR when a decision:

- is hard or expensive to reverse (database, module boundaries, auth model);
- will be questioned again ("why isn't this microservices?");
- deviates from the company standard on purpose.

Do not write one for a library upgrade, a naming preference, or anything a code
comment covers.

## Index

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [0001](0001-modular-monolith.md) | Modular monolith instead of microservices | Accepted | 2026-09-18 |
| [0002](0002-postgresql-as-primary-database.md) | PostgreSQL as the primary database | Accepted | 2026-09-18 |
| [0003](0003-package-and-module-structure.md) | Package and module structure | Accepted | 2026-09-18 |
| [0004](0004-jpa-entities-as-domain-model.md) | JPA entities as the domain model | Accepted | 2026-09-18 |
| [0005](0005-stateless-jwt-authentication.md) | Stateless JWT authentication | Accepted | 2026-09-18 |
| [0006](0006-transactional-outbox-for-module-events.md) | Transactional outbox for module events | Accepted | 2026-09-18 |
| [0007](0007-api-response-envelope-and-error-contract.md) | API response envelope and error contract | Accepted | 2026-09-18 |
| [0008](0008-real-postgresql-in-tests.md) | Real PostgreSQL in tests | Accepted | 2026-09-18 |
| [0009](0009-observability-stack.md) | Prometheus, Grafana and Loki as the observability stack | Accepted | 2026-09-18 |
| [0010](0010-idempotency-strategy.md) | Explicit idempotency guard instead of a transparent filter | Accepted | 2026-09-18 |
| [0011](0011-optimistic-locking.md) | Optimistic locking with `@Version` on `BaseEntity` | Accepted | 2026-09-18 |
| [0012](0012-postgis-in-tests.md) | No PostGIS types in migrations until tests can run PostGIS | Accepted | 2026-09-18 |
| [0013](0013-least-privilege-database-roles.md) | Least-privilege database roles for the application | Accepted | 2026-09-18 |
| [0014](0014-vendor-data-isolation.md) | Vendor data isolation enforced in the application, on a shared schema | Accepted | 2026-09-18 |
| [0015](0015-vendor-subscription-licensing.md) | Vendor licensing as a subscription, enforced by two gates | Accepted | 2026-09-18 |
| [0016](0016-gateway-credentials-in-the-database.md) | Payment gateway credentials in the database, encrypted, edited by an admin | Accepted | 2026-09-18 |

## How to add one

1. Take the next free number. Name the file
   `000N-short-kebab-case-title.md`.
2. Copy the template below. Keep every heading, even if a section is one line.
3. Fill in **Options considered** honestly: an ADR with one option is not a
   decision record, it is an announcement. Two or three real options, each with
   pros and cons.
4. Add a row to the index table above.
5. Open it in the same pull request as the code that implements the decision, or
   before it if the decision needs agreement first.

## Template

```markdown
# ADR-000N: Title

- **Status:** Accepted
- **Date:** YYYY-MM-DD
- **Deciders:** Backend team

## Context

What is true about the system and the requirements that makes this a decision.

## Problem

The one question this ADR answers, in a sentence.

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| A | | |
| B | | |

## Decision

What we chose. Present tense, no hedging.

## Reason

Why that option beat the others, against this project's constraints.

## Consequences

**Positive**

**Negative**

**Revisit when**
```

Statuses in use: `Proposed`, `Accepted`, `Superseded by ADR-000N`. A rejected
proposal stays in the tree with status `Rejected` if the reasoning is worth
keeping.
