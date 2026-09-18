# GroceryEcom engineering documentation

Backend for a multi-vendor grocery marketplace: one Spring Boot application
(Java 21, PostgreSQL 17) split into modules with build-enforced boundaries. Start
with the [backend developer guide](onboarding/backend-developer-guide.md) if you
are new.

## Onboarding

**[Backend developer guide](onboarding/backend-developer-guide.md)** — clone,
run, test and call the API; the `JAVA_HOME` and Windows-shell traps; a tour of
the packages and the four files worth reading first; what to read next; a
troubleshooting table.

## Architecture

**[System architecture](architecture/system-architecture.md)** — the shape of the
system, stage 1 sizing (100 vendors, 1,000,000 customer accounts, ~3,000
requests/second), the technology table, the three top-level packages, the
cross-cutting behaviours (one response shape, one trace id, one error handler),
and the list of what is not built yet.

**[Module architecture](architecture/module-architecture.md)** — the standard
layout of a business module (`contract`, `api`, `application`, `domain`,
`infrastructure`, `mapper`), the dependency rules, how modules talk through calls
and events, how `ModularityTest` enforces all of it, and the module roadmap.

**[Architecture decision records](architecture/adr/README.md)** — what an ADR is,
the index of the ones we have, and how to add one.

| ADR | Decision |
|-----|----------|
| [0001](architecture/adr/0001-modular-monolith.md) | Modular monolith instead of microservices |
| [0002](architecture/adr/0002-postgresql-as-primary-database.md) | PostgreSQL as the primary database |
| [0003](architecture/adr/0003-package-and-module-structure.md) | Package and module structure, and why it deviates from the generic standard |
| [0004](architecture/adr/0004-jpa-entities-as-domain-model.md) | JPA entities as the domain model |
| [0005](architecture/adr/0005-stateless-jwt-authentication.md) | Stateless JWT authentication |
| [0006](architecture/adr/0006-transactional-outbox-for-module-events.md) | Transactional outbox for module events |
| [0007](architecture/adr/0007-api-response-envelope-and-error-contract.md) | API response envelope and error contract |
| [0008](architecture/adr/0008-real-postgresql-in-tests.md) | Real PostgreSQL in tests |
| [0009](architecture/adr/0009-observability-stack.md) | Prometheus, Grafana and Loki as the observability stack |
| [0010](architecture/adr/0010-idempotency-strategy.md) | Explicit idempotency guard instead of a transparent filter |
| [0011](architecture/adr/0011-optimistic-locking.md) | Optimistic locking with `@Version` on `BaseEntity` |
| [0012](architecture/adr/0012-postgis-in-tests.md) | No PostGIS types in migrations until tests can run PostGIS |
| [0013](architecture/adr/0013-least-privilege-database-roles.md) | Least-privilege database roles for the application |
| [0014](architecture/adr/0014-vendor-data-isolation.md) | Vendor data isolation enforced in the application, on a shared schema |
| [0015](architecture/adr/0015-vendor-subscription-licensing.md) | Vendor licensing as a subscription, enforced by two gates |
| [0016](architecture/adr/0016-gateway-credentials-in-the-database.md) | Payment gateway credentials in the database, encrypted, edited by an admin |

## Engineering standards

**[Coding standard](engineering/coding-standard.md)** — formatting, which package
a class belongs in, visibility, naming, constructor injection, the Lombok
allow-list, required types (`Money`, `Instant`, public UUIDs), exception choice,
transaction placement, logging rules and what must never be logged, and how to
handle a SpotBugs finding.

**[API standard](engineering/api-standard.md)** — URL and versioning rules, the
endpoints that exist today, the `ApiResponse` envelope with real success, error
and validation examples, the full error-code table, how to raise an error, DTO
and validation rules, the trace id, and how to identify the caller in a
controller.

**[Database standard](engineering/database-standard.md)** — Flyway naming and the
never-edit-an-applied-migration rule, the two-identifier scheme, required column
types, constraints in the database rather than only in Java, vendor-ownership
indexing, history and soft-delete rules, partitioning preparation, the connection
budget, and how to test a migration.

**[Security standard](engineering/security-standard.md)** — token properties and
rules, the signing secret, the exact public-URL list and why changing it is a
security review, password hashing plus the two deliberate timing and ordering
behaviours in the login path, what is never returned, CORS and transport, secrets
handling, and a table of the real gaps.

**[Testing standard](engineering/testing-standard.md)** — how to run the suite,
the difference between a unit test and a `PostgresIntegrationTest` context test
and which to write, the rules (AssertJ, behaviour names, no sleeping, clean up
your data), and the tests that must stay covered because they encode decisions.

**[Observability standard](engineering/observability-standard.md)** — the signals
we have and the one we do not, log levels and what must never be logged, the
request log and the audit log, the trace id contract, the metrics that exist with
starting alert thresholds, how to debug a customer report from a trace id, and
the gaps. The local stack it describes lives in [ops/](../ops/README.md).

**[Code review checklist](engineering/code-review-checklist.md)** — an actual
checklist for the reviewer, grouped into boundaries, API, security, database,
code, tests and documentation.

## Development process

**[Feature development process](development/feature-development-process.md)** —
the ten-step loop from ticket to merge, where each kind of code goes, adding an
endpoint to an existing module, adding a whole new module step by step, doing
cross-module work, and what to do when a boundary check fails.

**[Git standard](development/git-standard.md)** — branch prefixes, conventional
commit prefixes with examples, what a pull request must say, the three CI jobs,
the Flyway migration-number conflict, and the process gaps.

**[Definition of done](development/definition-of-done.md)** — the checklist the
author runs before asking for a review, covering the change, build and tests, API
changes, database changes, security, configuration, documentation and git.

**[Deployment](development/deployment.md)** — running locally with and without
Docker, how the image is built, the health-check endpoints and why readiness
excludes Redis and RabbitMQ, the full environment-variable table, what CI does
and does not do, and production notes.

**[Razorpay setup](development/razorpay-setup.md)** — what to paste where to switch on
card payments, what the frontend does with the checkout response, the webhook, and a
symptom-to-fix table.

**[Database operations](development/database-operations.md)** — starting PostgreSQL,
the owner and runtime roles and why they are split, applying and checking
migrations, finding a slow query with `pg_stat_statements`, the tuned server
settings, backup and restore, and a symptom-to-fix table.

**[Staging](development/staging.md)** — there is no staging environment; the
intended pipeline from CI to production, what staging must have, the `staging`
profile and the settings that differ, and the concrete steps to create it. Every
section is marked as plan or as existing.

## Known gaps

Documented as gaps throughout, and collected here so nobody assumes otherwise:
only the `identity`, `vendor` and `billing` modules exist; RabbitMQ is configured
but unused by application code; security events are written to the `audit` logger
but there is no database-backed audit trail; nothing uses `IdempotencyGuard` yet;
the only payment gateway is Razorpay and it has never run against the real
Razorpay API; there are no invoices, no proration when changing plan mid-period
and no renewal reminder emails; there is no distributed tracing, no staging
environment and no automated deploy.
