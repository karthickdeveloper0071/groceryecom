# ADR-0001: Modular monolith instead of microservices

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

GroceryEcom is a multi-vendor grocery marketplace. Stage 1 targets 100 vendors,
up to 1,000,000 customer accounts, ~20,000 users online at peak, ~3,000
requests/second and ~35,000 orders/day. The business capabilities are known and
fairly coupled: checkout reads catalog and inventory, order writes payment and
delivery, everything reads identity. The team is small and there is no dedicated
platform or SRE group.

The capability boundaries themselves are reasonably clear (identity, vendor,
catalog, inventory, checkout, order, payment, delivery, notification), so the
question is not *what* the modules are but whether each should be its own
deployable service.

## Problem

Should each business capability be a separate deployable service, or should they
share one deployable with enforced internal boundaries?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| Microservices from day one | independent deploy and scaling per capability; failure isolation; forces explicit contracts | distributed transactions for order → payment → inventory; network calls where a method call would do; needs service discovery, tracing, per-service CI/CD and databases; a small team pays that cost on every feature |
| Single application, no internal boundaries | fastest to write; one transaction, one deploy | boundaries erode; in two years any class can reach any table and splitting anything becomes a rewrite |
| Modular monolith with enforced boundaries | one deploy, one transaction, one database; boundaries are compile-time checked so a module can be extracted later; no distributed-systems tax now | one bad module can take down the whole process; all modules scale together; boundary enforcement needs a real tool, not discipline |

## Decision

One Spring Boot application, split into modules whose boundaries are enforced at
build time by Spring Modulith 2.1.1 plus ArchUnit in
`src/test/java/com/groceryecom/ModularityTest.java`. It is deployed as 3-8
identical stateless instances behind a load balancer.

## Reason

At 3,000 requests/second one process type is not the bottleneck — the database
is, and that is addressed in [ADR-0002](0002-postgresql-as-primary-database.md).
Meanwhile the hardest parts of this domain (an order that reserves inventory and
takes a payment) are one local transaction in a monolith and a saga plus
compensation logic in microservices. We would pay that cost immediately for
scaling we do not need.

The boundaries are enforced, not suggested: a module reaching into another
module's internals fails the build. That keeps the option of extracting a module
into a service later, which is the real risk this decision has to cover.

## Consequences

**Positive**

- Order, payment and inventory changes commit in one database transaction.
- One build, one artifact, one deploy pipeline, one set of credentials.
- A module can be extracted later because its dependencies are already explicit.
- Cross-module events already go through an outbox
  ([ADR-0006](0006-transactional-outbox-for-module-events.md)), which is what an
  extracted service would need anyway.

**Negative**

- An out-of-memory error or a hot loop in any module affects every module;
  `-XX:+ExitOnOutOfMemoryError` at least makes the failure clean.
- Modules cannot be scaled separately. A CPU-heavy report competes with checkout.
- The whole application is deployed for a one-line change in one module.
- Nothing stops the team from adding a module that just wraps another module's
  tables, which the boundary checks cannot detect.

**Revisit when**

- Sustained traffic exceeds roughly 5,000 requests/second, or one module's
  resource profile is clearly different from the rest (image processing, search
  indexing, report generation).
- A module needs an independent release cadence for business reasons.
- More than one team owns modules in this repository.

Extraction candidates, in order: `notification` (events only, no synchronous
callers), then `payment`, then `catalog` search.
