# GroceryEcom

Backend for a multi-vendor grocery platform: vendors sell groceries, customers order from them, and the platform handles payments, payouts and delivery.

Stage 1 target: 100 vendors and up to 1,000,000 customer accounts (about 20,000 users online at peak).

**Status: the foundation, with no business logic on top of it yet.** What works today is
everything underneath a feature — security, error handling, the database, the boundaries
and the tests that enforce them. Business modules are written on top of it; see
[module architecture](docs/architecture/module-architecture.md) for the layout each one
follows.

## Stack

| Area | Choice |
|------|--------|
| Language and framework | Java 21, Spring Boot 4.1 |
| Architecture | Modular monolith with module boundaries enforced by Spring Modulith |
| Database | PostgreSQL 17 (PostGIS image), schema managed by Flyway |
| Cache | Redis |
| Events between modules | Spring Modulith events, written to an outbox table in the same transaction |
| Auth | Stateless JWT (HS512 access and refresh tokens) |
| API docs | OpenAPI at `/api/swagger-ui.html` |

## Quick start

Requirements: JDK 21 and Docker (Docker Desktop on Windows or macOS).

```bash
docker compose up -d
./mvnw spring-boot:run
```

That starts PostgreSQL and Redis. On the first start of an empty database
volume, `ops/postgres/init` also creates the runtime role the API uses and the
`pg_stat_statements` extension. If the volume is older than those scripts, reset it
with `docker compose down -v && docker compose up -d`.

Or run everything in Docker (copy `.env.example` to `.env` and set `JWT_SECRET` first):

```bash
docker compose --profile app up -d --build
```

The API runs at `http://localhost:8080/api`. Check it with `curl http://localhost:8080/api/actuator/health`.

Run the tests (no Docker needed; they start an embedded PostgreSQL):

```bash
./mvnw verify
```

## Project layout

```
src/main/java/com/groceryecom/
├── GroceryEcomApplication.java
├── shared/              Money, BaseEntity, ApiResponse, base exceptions
├── platform/            security (JWT), error handling, trace ids, audit log,
│                        rate limiting, secret encryption, scheduling, OpenAPI, cache
└── modules/             business modules go here — none yet
src/main/resources/
├── application.yml
└── db/migration/        Flyway migrations, named V<n>__<module>_<change>.sql
```

**There is no business module yet.** The foundation is built and tested; the features
are not. Each module you add looks like this:

```
modules/<name>/
├── package-info.java   @ApplicationModule — without it, the boundary checks ignore the module
├── contract/           the only package other modules may use: events, enums, interfaces
├── api/                REST controllers + dto/ request and response records
├── application/        one service per use case
├── domain/             entities and Spring Data repositories
├── infrastructure/     adapters to external systems — only when the module needs one
└── mapper/             entity to response mapping
```

`ModularityTest` fails the build if a module reaches past another module's `contract`
package, or if two modules form a cycle.

## Documentation

- [docs/README.md](docs/README.md): index of all engineering documentation
- [docs/onboarding/backend-developer-guide.md](docs/onboarding/backend-developer-guide.md): setup, run, test, troubleshooting
- [docs/architecture/system-architecture.md](docs/architecture/system-architecture.md): shape, sizing, stack, cross-cutting behaviour
- [docs/architecture/module-architecture.md](docs/architecture/module-architecture.md): module layout, dependency rules, roadmap
- [docs/architecture/adr/README.md](docs/architecture/adr/README.md): architecture decision records
- [docs/development/deployment.md](docs/development/deployment.md): running and deploying with Docker
- [docs/development/database-operations.md](docs/development/database-operations.md): PostgreSQL roles, migrations, slow queries, backups
