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

The API runs at `http://localhost:8080/api`. Check it with `curl http://localhost:8080/api/actuator/health`.

Run the tests (no Docker needed; they start an embedded PostgreSQL):

```bash
./mvnw verify
```

## Project layout

```
src/main/java/com/groceryecom/
├── GroceryEcomApplication.java
├── shared/          Money, BaseEntity, ApiResponse, base exceptions
├── platform/        security (JWT), error handling, request ids, OpenAPI, cache
└── modules/
    └── identity/    accounts, roles, login, tokens
        ├── api/        the only package other modules may use
        ├── internal/   entities, repositories, services
        └── web/        controllers and request/response records
src/main/resources/
├── application.yml
└── db/migration/    Flyway migrations, named V<n>__<module>_<change>.sql
```

## Documentation

- [docs/architecture.md](docs/architecture.md): modules, dependency rules, data rules and roadmap
- [docs/development.md](docs/development.md): setup, configuration, conventions, and how to add a module
