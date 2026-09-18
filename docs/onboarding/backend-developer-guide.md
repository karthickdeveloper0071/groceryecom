# Backend developer guide

Getting from a clone to a running API and a green build, plus what to read next.

## What this is

GroceryEcom is the backend for a multi-vendor grocery marketplace: vendors sell
groceries, customers order from one or more vendors, and the platform handles
payments, payouts and delivery. It is one Spring Boot application split into
modules with enforced boundaries — a modular monolith, not microservices.

One business module exists today: `identity` (accounts, roles, login, tokens).
Everything else is a plan.

## Requirements

| Tool | Version | Notes |
|------|---------|-------|
| JDK | **21** | Maven reads `JAVA_HOME`, not the `java` on your PATH |
| Docker | any recent | Only for PostgreSQL, Redis and RabbitMQ. Tests do not need it. |
| Git | any recent | |

No Maven install needed; use the wrapper (`./mvnw`).

### The JAVA_HOME trap

Maven uses `JAVA_HOME`. If yours points at a JDK 17 install, the build fails on
Java 21 syntax even though `java -version` prints 21. Check both:

```bash
java -version          # what is on your PATH
echo $JAVA_HOME        # what Maven will use
```

If `JAVA_HOME` is a JDK 17, either change it for good or set it for the command:

```bash
# Git Bash, one command
JAVA_HOME="/c/Program Files/Java/jdk-21" ./mvnw verify
```

```powershell
# PowerShell, one session
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\mvnw.cmd verify
```

### Windows shells

- **Git Bash:** `./mvnw <goal>`
- **PowerShell or cmd:** `mvnw.cmd <goal>`

`./mvnw` in PowerShell does not work. The rest of this documentation writes
`./mvnw`; substitute `mvnw.cmd` if you are in PowerShell.

## Set up

```bash
git clone https://github.com/karthickdeveloper0071/groceryecom.git
cd groceryecom
docker compose up -d        # PostgreSQL, Redis, RabbitMQ
./mvnw spring-boot:run
```

The defaults in `application.yml` match `docker-compose.yml`, so no
configuration is needed for local development.

| URL | What |
|-----|------|
| http://localhost:8080/api | API base |
| http://localhost:8080/api/swagger-ui.html | Interactive API docs; use this first |
| http://localhost:8080/api/v3/api-docs | OpenAPI document |
| http://localhost:8080/api/actuator/health | Health |
| http://localhost:15672 | RabbitMQ console (guest / guest) |

Check it works:

```bash
curl http://localhost:8080/api/actuator/health
```

## Run the tests

```bash
./mvnw verify
```

That is the whole suite — unit tests, context tests, module boundary rules and
SpotBugs static analysis — and it is exactly what CI runs. **No Docker
required:** tests that start Spring boot a real embedded PostgreSQL 17 in-process
and run the production Flyway migrations against it. The first run downloads and
unpacks the PostgreSQL binaries, so it takes longer than the rest.

Useful variants:

```bash
./mvnw test                    # skip packaging and SpotBugs
./mvnw test -Dtest=MoneyTest   # one class
```

35 tests at the time of writing. Details:
[testing standard](../engineering/testing-standard.md).

## Try the API

Register, then use `data.accessToken` from the response as a bearer token:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"asha","email":"asha@example.com","password":"correct-horse"}'

curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
```

Endpoints that exist today:

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/auth/register` | public, always creates a `CUSTOMER` |
| POST | `/api/v1/auth/login` | public, username **or** email |
| POST | `/api/v1/auth/refresh-token` | public |
| GET | `/api/v1/auth/me` | bearer token |
| POST | `/api/v1/auth/change-password` | bearer token |

Access tokens last 15 minutes, refresh tokens 7 days.

## Run everything in Docker

```bash
cp .env.example .env     # put a long random value in JWT_SECRET
docker compose --profile app up -d --build
docker compose logs -f app
```

The `app` service refuses to start without `JWT_SECRET`. That is deliberate: a
shared, well-known signing key would let anyone mint valid tokens. Full details
in [deployment.md](../development/deployment.md).

## Find your way around

```
src/main/java/com/groceryecom/
├── GroceryEcomApplication.java
├── shared/          Money, BaseEntity, ApiResponse, base exceptions — depends on nothing
├── platform/        security (JWT), web (errors, trace ids, OpenAPI), cache
└── modules/
    └── identity/    accounts, roles, login, tokens
        ├── contract/        the only package other modules may use
        ├── api/             controllers + dto/
        ├── application/     one service per use case
        ├── domain/          entities, repositories
        └── mapper/          entity → response
src/main/resources/
├── application.yml
└── db/migration/    Flyway migrations, V<n>__<module>_<change>.sql
src/test/java/com/groceryecom/
├── PostgresIntegrationTest.java   base class for tests that start Spring
└── ModularityTest.java            module boundary rules
```

Four things to read in the code, in this order:
`shared/web/ApiResponse.java` (the one response shape),
`platform/web/GlobalExceptionHandler.java` (how an exception becomes a response),
`platform/security/SecurityConfig.java` (which URLs are public, and nothing else
is), then `modules/identity/` (the layout every other module will copy).

## Two things that surprise people

**`api` and `contract` are different.** `api/` is the REST layer (controllers and
DTOs). `contract/` is the cross-module public surface — events, enums, service
interfaces. Another module may import `contract` and nothing else.
[ADR-0003](../architecture/adr/0003-package-and-module-structure.md) explains
why.

**The boundary rules are a test.** `ModularityTest` fails the build if a module
reaches past another module's `contract`, if two modules form a cycle, or if
`shared`/`platform` depends on a business module. When it fails, the dependency
is wrong, not the test.

## Read next

| Doc | Why |
|-----|-----|
| [System architecture](../architecture/system-architecture.md) | the shape of the system, sizing, what is not built |
| [Module architecture](../architecture/module-architecture.md) | the module layout and the dependency rules you will be held to |
| [Coding standard](../engineering/coding-standard.md) | where classes go, Lombok limits, logging rules |
| [API standard](../engineering/api-standard.md) | the response envelope and the error codes |
| [Feature development process](../development/feature-development-process.md) | how to add an endpoint or a module |
| [Definition of done](../development/definition-of-done.md) | check this before asking for a review |
| [ADRs](../architecture/adr/README.md) | why the project is the way it is |

## Know the gaps before you trust the docs

- Only `identity` exists. The other module names are scope, not code.
- RabbitMQ runs and is configured, but no application code publishes to it.
- No rate limiting, no token revocation (so no real logout), no audit log.
- No staging environment and no automated deploy.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Build fails on Java syntax although `java -version` says 21 | `JAVA_HOME` points at another JDK | see [the JAVA_HOME trap](#the-java_home-trap) |
| `./mvnw` not recognised in PowerShell | wrong wrapper for the shell | use `mvnw.cmd` |
| App fails at startup: schema validation | an entity does not match the Flyway schema | fix the entity or add a migration; `ddl-auto=validate` never alters the schema |
| App refuses to start over the JWT secret | `JWT_SECRET` is shorter than 64 bytes | generate one: `openssl rand -base64 64 \| tr -d '\n'` |
| Connection refused on port 5432 | infrastructure not running | `docker compose up -d` |
| First `verify` is very slow | PostgreSQL binaries being downloaded | expected once per machine |
| Tests fail on data that already exists | one PostgreSQL server per JVM, so classes share a database | make your test data unique and clean it up |
| Need a clean database | volume still holds old data | `docker compose down -v && docker compose up -d` |
