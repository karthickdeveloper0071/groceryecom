# Staging

**There is no staging environment.** Nothing in this document describes a running
system. It describes the pipeline we intend to have, what an environment must
provide before we call it staging, and the work someone has to do to create it.
Every section is marked **Exists** or **Plan**.

Today (**Exists**): CI runs `./mvnw -B verify`, a Trivy scan and a Docker image
build on every pull request. The image is built and thrown away. There is no
registry, no deploy job, no environment. Deployment is manual, from a locally
built image. See [deployment.md](deployment.md).

## Intended pipeline (Plan)

```
push / PR
  -> CI: verify + Trivy + image build           (Exists)
  -> build and push image tagged with the commit sha   (Plan)
  -> deploy to staging, Flyway runs on startup         (Plan)
  -> smoke tests against staging                       (Plan)
  -> manual approval                                   (Plan)
  -> deploy the same image to production               (Plan)
```

Rules that go with it:

- The artefact is one image, tagged with the commit sha. Production runs the
  **same** image staging ran. Nothing is rebuilt between the two, or the two
  environments are not comparable.
- Only `main` deploys to staging. Pull request branches stop at CI.
- The manual approval is a person, not a timer.
- Rollback is redeploying the previous sha, which only works while migrations
  stay backward compatible with the previous version. That rule already applies
  ([database standard](../engineering/database-standard.md)).

## What staging must have (Plan)

| Requirement | Why |
|-------------|-----|
| Its own PostgreSQL instance | A shared database means a staging test can corrupt production data. Non-negotiable. |
| Its own secrets | A different `JWT_SECRET`, different database credentials. A token minted in staging must be useless in production. |
| Its own Redis and RabbitMQ | Same reason. A shared queue would deliver staging events to production consumers. |
| Production-like configuration | Same profile shape, same JSON logging, same connection pool behaviour, same TLS termination. Differences that are not deliberate make staging results meaningless. |
| Seeded test data | Vendors, products and customer accounts created by a seed script that lives in the repository, so anyone can reset staging to a known state. |
| **Never production data** | No dump, no subset, no "anonymised" copy. Customer addresses and order history do not belong in an environment with weaker access control. |
| Its own observability | Its own Prometheus and Loki, or at least an `env=staging` label. Staging noise must not be mistaken for a production incident. |
| Smaller scale, not different | One or two app instances against a single database is fine. Sizing may differ; topology should not. |

## The `staging` profile (Exists, partly)

`application.yml` already has a profile group `staging,prod` that switches
console logging to ECS JSON. That is the only staging-specific configuration
that exists today. Everything else comes from environment variables, which are
the same set production uses; see the table in
[deployment.md](deployment.md#environment-variables).

What should differ in staging once it exists (**Plan**):

| Setting | Staging | Production |
|---------|---------|------------|
| `SPRING_PROFILES_ACTIVE` | `staging` | `prod` |
| `JWT_SECRET` | its own value from the secrets manager | its own, different value |
| `DB_URL` | the staging database | the production database |
| `DB_POOL_SIZE` | smaller, matching the instance count | sized to the connection budget |
| `CORS_ALLOWED_ORIGINS` | staging frontend origins only | real origins only |
| `logging.level.com.groceryecom` | `DEBUG` is acceptable while investigating | `INFO` |
| Payment and other external providers | sandbox or test credentials | live credentials |
| Outbound email and SMS | captured, not delivered to real recipients | live |
| Seed and reset job | allowed | must not exist |

Add a `staging`-only block to `application.yml` only for something that genuinely
differs. Every extra difference is a way for "it worked in staging" to be wrong.

## Smoke tests (Plan)

A smoke test proves the deployment is alive, not that the feature is correct —
correctness is the job of the test suite. Keep it to about a minute:

1. `GET /api/actuator/health/readiness` returns `UP`.
2. `GET /api/actuator/health` returns `UP`, including Redis and RabbitMQ.
3. `GET /api/actuator/info` reports the expected commit sha. This is what catches
   a deploy that silently did not happen.
4. `POST /api/v1/auth/register` with a generated username, then
   `POST /api/v1/auth/login`, then a call to an authenticated endpoint with the
   returned token. That exercises the app, the database and the JWT path.
5. Flyway reports no pending migrations.

A failing smoke test blocks the approval step and triggers a rollback to the
previous sha.

## Steps to create it (Plan)

Concrete, in order. Each is a separate piece of work.

1. **Pick where it runs.** A container platform, or a single VM with
   `docker compose`. The application is stateless, so either works; the decision
   is really who maintains it.
2. **Create a container registry** and add a push step to
   `.github/workflows/ci.yml`, tagging the image with `${{ github.sha }}` on
   pushes to `main` only.
3. **Provision a managed PostgreSQL 17 instance** for staging. PostGIS matters
   later, and is already a constraint on tests
   ([ADR-0012](../architecture/adr/0012-postgis-in-tests.md)).
4. **Provision Redis and RabbitMQ** for staging.
5. **Choose a secrets store** and put `JWT_SECRET`, database credentials and
   provider keys in it. Generate the secret with
   `openssl rand -base64 64 | tr -d '\n'`. Never reuse the production value, and
   never commit either.
6. **Create a GitHub environment** called `staging`, hold the deploy credentials
   there, and add a `production` environment with a required reviewer so the
   approval step has teeth.
7. **Write the deploy job**: pull the image by sha, set the environment
   variables, roll instances one at a time, wait for readiness. Migrations run at
   startup; deploy one instance first so instances do not race.
8. **Write the seed script** as a repeatable Flyway migration or a standalone
   job that only runs when the profile is `staging`, and make resetting staging a
   single command.
9. **Write the smoke tests** as a job that runs after the deploy and fails the
   pipeline.
10. **Stand up observability** for staging:
    [ADR-0009](../architecture/adr/0009-observability-stack.md) and
    [ops/README.md](../../ops/README.md) describe the stack; staging needs it as
    a deployed service with real credentials, not the local compose overlay.
11. **Write the runbook**: how to deploy, how to roll back, who approves
    production, where the logs are. Then update
    [deployment.md](deployment.md) and the
    [git standard](git-standard.md), and delete the "no staging environment"
    line from [the docs index](../README.md).

## Until then

- Nothing verifies the application outside CI and a developer's laptop.
- The first real deploy is also the first time the image runs anywhere but a
  laptop, which is the risk this environment exists to remove.
- Anything that cannot be tested locally with `docker compose --profile app`
  cannot be tested at all yet. Treat that as a reason to keep local setup
  working, not as a reason to skip testing.
