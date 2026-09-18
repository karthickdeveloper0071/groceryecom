# Running and deploying with Docker

## Local: infrastructure only (usual while coding)

Runs PostgreSQL, Redis and RabbitMQ in containers; you run the app from your IDE or Maven.

```bash
docker compose up -d
./mvnw spring-boot:run
```

## Local: everything in Docker

```bash
cp .env.example .env     # then put a long random value in JWT_SECRET
docker compose --profile app up -d --build
docker compose logs -f app
```

The API is at `http://localhost:8080/api`. Stop it with `docker compose --profile app down` (add `-v` to also delete the database volume).

The `app` service refuses to start without `JWT_SECRET`. That is deliberate: a shared, well-known signing key would let anyone mint valid tokens.

## The image

`Dockerfile` builds in two stages:

1. **Build stage** downloads dependencies first (cached until `pom.xml` changes), compiles, then splits the jar into layers.
2. **Runtime stage** is a JRE-only Alpine image, copies the layers from least to most frequently changed, and runs as the non-root user `app`.

Notes:

- Tests do not run during the image build. CI runs them; keep it that way so deploys stay fast.
- `JAVA_TOOL_OPTIONS` sets `-XX:MaxRAMPercentage=75`, so the heap follows the container memory limit. Set a memory limit on the container in production.
- `-XX:+ExitOnOutOfMemoryError` stops the container instead of leaving it half-working, so the orchestrator restarts it.

## Health checks

| Endpoint | Use |
|----------|-----|
| `/api/actuator/health/liveness` | Is the process alive? Failing means restart. Used by the image's HEALTHCHECK. |
| `/api/actuator/health/readiness` | Should it receive traffic? Includes the database only. |
| `/api/actuator/health` | Overall status, including Redis and RabbitMQ. |

Redis or RabbitMQ being down does not fail readiness on purpose: those outages break some features, but taking every instance out of rotation (or restarting them) would turn a partial outage into a full one.

The app shuts down gracefully: it finishes in-flight requests for up to 30 seconds. Give containers at least that long to stop.

## Environment variables

Everything in `src/main/resources/application.yml` can be overridden by an
environment variable. The defaults match `docker-compose.yml`, so local
development needs none of them.

| Variable | Default | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/grocery_ecom` | |
| `DB_USERNAME` / `DB_PASSWORD` | `grocery` / `grocery` | |
| `DB_POOL_SIZE` | `20` | Per app instance |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / empty | |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `localhost` / `5672` / `guest` / `guest` | Configured but not used by application code yet |
| `JWT_SECRET` | a local-only value | **Required** outside local development; at least 64 bytes. The app refuses to start with a shorter one. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Comma-separated origin **patterns**, e.g. `https://*.groceryecom.com` for every vendor subdomain |
| `PORT` | `8080` | |

Secrets never go in Git. `.env` is git-ignored; `.env.example` documents the
variables. In production, set at least:

| Variable | Notes |
|----------|-------|
| `JWT_SECRET` | 64+ characters, from a secrets manager, different per environment |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Managed PostgreSQL, not a container |
| `REDIS_HOST`, `RABBITMQ_HOST` | Managed services |
| `CORS_ALLOWED_ORIGINS` | Your real origins only, for example `https://shop.groceryecom.com,https://admin.groceryecom.com,https://*.groceryecom.com`. Never `*` alone. |
| `DB_POOL_SIZE` | Keep instances × pool size below the database connection limit |

## What CI does and does not do

`.github/workflows/ci.yml` runs on every pull request and every push to `main`:
`./mvnw -B verify` (tests plus SpotBugs), a Trivy dependency and secret scan, and
a Docker image build that proves the `Dockerfile` still works.

Nothing is published or deployed. The image is built and discarded — there is no
registry push, no deploy job and **no staging environment**. Deployment today is
a manual step from a built image. Details of the jobs are in the
[git standard](git-standard.md).

## Production notes

- Run several instances behind a load balancer; they are stateless. `server.forward-headers-strategy` is set, so client IPs and redirects work behind a proxy.
- Use managed PostgreSQL (backups, point-in-time recovery, a read replica), not a database container.
- Flyway migrations run at startup. Deploy one instance first, or run migrations as a separate step, so several instances don't race; Flyway locks, but a single-runner step is clearer.
- Keep migrations backward compatible with the running version, so a rollback doesn't break.
- Do not expose port 15672 (RabbitMQ console) publicly.
- Stage 1 sizing is 3-8 app instances against one PostgreSQL primary plus a read replica; see [system-architecture.md](../architecture/system-architecture.md).

## See also

- [Backend developer guide](../onboarding/backend-developer-guide.md) — local setup
- [Database standard](../engineering/database-standard.md) — migration rules, connection budget
- [Security standard](../engineering/security-standard.md) — secrets, CORS, TLS
- [System architecture](../architecture/system-architecture.md) — sizing and topology
