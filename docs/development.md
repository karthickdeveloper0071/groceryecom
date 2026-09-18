# Development guide

## Requirements

- JDK 21. Check with `java -version`, and make sure `JAVA_HOME` points to JDK 21: Maven uses `JAVA_HOME`, not the `java` on your PATH.
- Docker, to run PostgreSQL, Redis and RabbitMQ locally. Tests don't need it.

## Run locally

```bash
docker compose up -d
./mvnw spring-boot:run
```

| URL | What |
|-----|------|
| http://localhost:8080/api | API base |
| http://localhost:8080/api/swagger-ui.html | Interactive API docs |
| http://localhost:8080/api/actuator/health | Health check |
| http://localhost:15672 | RabbitMQ console (guest / guest) |

## Test

```bash
./mvnw verify
```

Tests that start the application extend `PostgresIntegrationTest`, which runs a real embedded PostgreSQL 17 with the production Flyway migrations. Pure unit tests (such as `MoneyTest`) don't start Spring.

## Configuration

All settings live in `src/main/resources/application.yml`. The defaults match `docker-compose.yml`. Override them with environment variables:

| Variable | Default | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/grocery_ecom` | |
| `DB_USERNAME` / `DB_PASSWORD` | `grocery` / `grocery` | |
| `DB_POOL_SIZE` | `20` | Per app instance |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / empty | |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `localhost` / `5672` / `guest` / `guest` | |
| `JWT_SECRET` | A local-only value | **Required** outside local development; at least 64 bytes. The app refuses to start with a shorter one. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated |
| `PORT` | `8080` | |

## API conventions

- URLs are versioned: `/api/v1/...`. Vendor routes will live under `/api/v1/vendor/...` and admin routes under `/api/v1/admin/...`.
- Every response uses the `ApiResponse` envelope: `success`, `data`, `message`, `errorCode`, `errors`, `timestamp`, `requestId`.
- The HTTP status is the outcome; `errorCode` is a stable reason clients can switch on (for example `USERNAME_EXISTS`).
- Every response has an `X-Request-Id` header. Log lines include the same id, so a customer's error report can be matched to server logs.
- Request and response bodies are Java records in the module's `web/dto` package, validated with Bean Validation annotations.
- Business errors throw a subclass of `ApplicationException` (`ValidationException` 400, `UnauthorizedException` 401, `NotFoundException` 404, `ConflictException` 409). Anything else becomes a 500 with no internal details.
- Get the logged-in user in a controller with `@AuthenticationPrincipal AuthenticatedUser user`.

## Database migrations

- Add a new file in `src/main/resources/db/migration`, named `V<next number>__<module>_<change>.sql`, for example `V3__vendor_create_vendors.sql`.
- Never edit a migration that has run anywhere shared. Write a new one instead.
- Tests run every migration against PostgreSQL, so a broken migration fails the build.

## Adding a module

1. Create `src/main/java/com/groceryecom/modules/<name>/package-info.java`:
   ```java
   @ApplicationModule(displayName = "Vendors")
   package com.groceryecom.modules.vendor;

   import org.springframework.modulith.ApplicationModule;
   ```
2. Create `api/package-info.java` with `@NamedInterface("api")`, then `internal/` and `web/dto/` packages.
3. Put entities, repositories and services in `internal`. Make them package-private where possible.
4. Put anything another module needs in `api`: a service interface, event records, enums.
5. Add a Flyway migration for the module's tables.
6. Add the module's public endpoints to `SecurityConfig` only if they must be reachable without login.
7. Run `./mvnw verify`. `ModularityTest` confirms the new module respects the boundaries.

## Code style

- `.editorconfig` sets formatting: 4 spaces for Java, 2 for YAML, LF line endings.
- Use constructor injection, not field injection.
- Use Lombok `@Getter`/`@Setter` on entities, never `@Data`.
- Use `Instant` for timestamps and `Money` for amounts.
