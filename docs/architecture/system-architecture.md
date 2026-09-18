# System architecture

GroceryEcom is the backend for a multi-vendor grocery marketplace: vendors sell
groceries, customers order from one or more vendors, and the platform handles
payments, payouts and delivery.

## Shape

One Spring Boot application — a modular monolith — deployed as several identical,
stateless instances behind a load balancer. Business capabilities live in modules
with enforced walls, so a module can later move into its own service without a
rewrite. See [ADR-0001](adr/0001-modular-monolith.md) for why this instead of
microservices.

```
                 ┌──────────────┐
  clients ──────►│ load balancer│
                 └──────┬───────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
   ┌─────────┐     ┌─────────┐     ┌─────────┐
   │  app 1  │ ... │  app n  │     │  app n  │   3-8 stateless instances
   └────┬────┘     └────┬────┘     └────┬────┘
        │               │               │
        ├───────────────┴───────────────┤
        ▼                               ▼
 ┌───────────────┐              ┌──────────────────────────┐
 │ PostgreSQL 17 │              │ Redis                    │
 │ primary       │              │ token revocation,        │
 │  + read       │              │ rate limits, cache       │
 │    replica    │              └──────────────────────────┘
 └───────────────┘
```

## Stage 1 sizing

| Dimension | Target |
|-----------|--------|
| Vendors | 100 |
| Customer accounts | up to 1,000,000 |
| Users online at peak | ~20,000 |
| Requests per second | ~3,000 |
| Orders per day | ~35,000 |
| App instances | 3-8, stateless |
| Database | one PostgreSQL primary + one read replica |

No sharding, no service split. One primary handles this load comfortably; the
read replica exists for reporting and read-heavy queries. If those numbers stop
holding, the revisit triggers are recorded in
[ADR-0001](adr/0001-modular-monolith.md) and
[ADR-0002](adr/0002-postgresql-as-primary-database.md).

## Technology

| Area | Choice | Notes |
|------|--------|-------|
| Language, runtime | Java 21 | virtual threads enabled (`spring.threads.virtual.enabled`) |
| Framework | Spring Boot 4.1 | Web MVC, Security, Data JPA, Actuator |
| Module boundaries | Spring Modulith 2.1.1 | `@ApplicationModule`, `verify()` in tests |
| Database | PostgreSQL 17 (PostGIS image) | Flyway owns the schema |
| Cache | Redis | `spring.cache.type=redis`, key prefix `ecom:` |
| Events between modules | Spring Modulith events plus the `event_publication` outbox table. No broker: nothing outside the application consumes events yet |
| Auth | jjwt 0.13, HS512 access + refresh tokens | stateless, no sessions |
| API docs | springdoc-openapi 3.1.1 | `/api/swagger-ui.html` |
| Boilerplate | Lombok | `@Getter`/`@Setter` only, never `@Data` |
| Tests | JUnit 5, Mockito, AssertJ, zonky embedded-postgres | real PostgreSQL, no Docker |
| Build, ship | Maven wrapper, Docker, docker compose, GitHub Actions | |

## Top-level packages

Everything lives under `com.groceryecom`.

| Package | Role | May depend on |
|---------|------|---------------|
| `shared` | business-agnostic code with no dependencies: `Money`, `BaseEntity`, `ApiResponse`, base exceptions | nothing in this project |
| `platform` | technical framework wiring: `security`, `web`, `cache` | `shared` |
| `modules.<name>` | one business capability each | `shared`, `platform`, other modules' `contract` packages |

`shared` and `platform` are Spring Modulith **OPEN** modules, so business modules
may use any of their packages without a named interface. Business modules are
closed: only their `contract` package is reachable from outside.

Why two foundation packages instead of one `common`: see
[ADR-0003](adr/0003-package-and-module-structure.md).

Details of the module layout and the dependency rules are in
[module-architecture.md](module-architecture.md).

## Cross-cutting behaviour

- **One response shape.** Every response, success or error, is the `ApiResponse`
  envelope built in `shared.web.ApiResponse`. See
  [ADR-0007](adr/0007-api-response-envelope-and-error-contract.md) and the
  [API standard](../engineering/api-standard.md).
- **One trace id per request.** `platform.web.RequestIdFilter` reuses a
  well-formed inbound `X-Request-Id` or generates a UUID, puts it in the SLF4J
  MDC, returns it in the response header and in the body. The log pattern
  includes `[%X{traceId:-}]`, so a customer's error report maps to log lines.
- **Errors are handled in one place.** `platform.web.GlobalExceptionHandler`
  extends Spring's `ResponseEntityExceptionHandler`, so standard MVC failures
  keep their correct status and only the body is replaced. Stack traces and
  internal messages never reach a client.
- **Security is stateless.** No server-side session. See
  [ADR-0005](adr/0005-stateless-jwt-authentication.md) and the
  [security standard](../engineering/security-standard.md).
- **Events between modules go through the database.** A publication row is
  written to `event_publication` in the same transaction as the business change.
  See [ADR-0006](adr/0006-transactional-outbox-for-module-events.md).

## Runtime and operations

- Health groups: readiness includes only `readinessState` and `db`, so a Redis or
  A Redis outage degrades features but does not remove an instance from the load
  balancer. Liveness is process-only.
- Graceful shutdown with a 30 second limit per phase; give containers at least
  that long to stop.
- `server.forward-headers-strategy=framework`, so client IPs and redirects are
  right behind a proxy.
- Flyway migrations run at application startup. Deployment details are in
  [deployment.md](../development/deployment.md).

## Not built yet

These are real gaps, not omissions from this document:

| Gap | Impact |
|-----|--------|
| Only the `identity` module exists | everything else in the module table is a plan |
| No message broker | module events are in-process plus the outbox table; a broker returns when something outside the application has to consume them |
| No rate limiting | login and registration can be hammered |
| No token revocation or deny-list | logout cannot invalidate an issued access token |
| No audit log | no record of who changed what |
| No staging environment | changes go from a developer machine and CI to production |
