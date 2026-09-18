# GroceryEcom

Backend for a multi-vendor grocery marketplace: vendors sell groceries, customers order from one or more vendors, and the platform handles payments, payouts and delivery.

Stage 1 target: 100 vendors and up to 1,000,000 customer accounts (about 20,000 users online at peak).

## Stack

| Area | Choice |
|------|--------|
| Language and framework | Java 21, Spring Boot 4.1 |
| Architecture | Modular monolith with module boundaries enforced by Spring Modulith |
| Database | PostgreSQL 17 (PostGIS image), schema managed by Flyway |
| Cache | Redis |
| Messaging | RabbitMQ (for events between modules and background work) |
| Auth | Stateless JWT (HS512 access and refresh tokens) |
| API docs | OpenAPI at `/api/swagger-ui.html` |

## Quick start

Requirements: JDK 21 and Docker.

```bash
docker compose up -d
./mvnw spring-boot:run
```

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
├── platform/            security (JWT), error handling, trace ids, OpenAPI, cache
└── modules/
    └── identity/        accounts, roles, login, tokens
        ├── contract/      types other modules may use: events, enums
        ├── api/           REST controllers + dto/ request and response records
        ├── application/   one service per use case
        ├── domain/        entities and repositories
        └── mapper/        entity to response mapping
src/main/resources/
├── application.yml
└── db/migration/        Flyway migrations, named V<n>__<module>_<change>.sql
```

Every business module follows that same layout; `infrastructure/` is added when a module
talks to an external system. `ModularityTest` fails the build if a module reaches past
another module's `contract` package.

## Documentation

- [docs/README.md](docs/README.md): index of all engineering documentation
- [docs/onboarding/backend-developer-guide.md](docs/onboarding/backend-developer-guide.md): setup, run, test, troubleshooting
- [docs/architecture/system-architecture.md](docs/architecture/system-architecture.md): shape, sizing, stack, cross-cutting behaviour
- [docs/architecture/module-architecture.md](docs/architecture/module-architecture.md): module layout, dependency rules, roadmap
- [docs/architecture/adr/README.md](docs/architecture/adr/README.md): architecture decision records
- [docs/development/deployment.md](docs/development/deployment.md): running and deploying with Docker
