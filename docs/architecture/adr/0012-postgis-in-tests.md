# ADR-0012: No PostGIS types in migrations until tests can run PostGIS

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

There is a mismatch between the database developers run and the database tests
run.

`docker-compose.yml` starts `postgis/postgis:17-3.5`, chosen because delivery
zones were expected to need geometry. Context tests do not use that container.
They extend `PostgresIntegrationTest`, which starts zonky embedded PostgreSQL 17
inside the test JVM and runs the production Flyway migrations against it
([ADR-0008](0008-real-postgresql-in-tests.md)). The embedded binaries have **no
PostGIS extension**, and no `CREATE EXTENSION postgis` will fix that.

So a migration containing `geometry`, `geography` or `CREATE EXTENSION postgis`
would apply cleanly on a developer's local Docker database and fail every context
test and the CI build. The failure would appear as a Flyway error in an unrelated
test, days after the migration was written, to whoever pulled `main` next.

The `delivery` module does not exist yet. Nothing in `src/main/resources/db/migration`
uses a spatial type today, so this is a decision we can make before it costs
anything.

## Problem

May a migration use PostGIS types while tests cannot run PostGIS?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| Allow PostGIS and let the affected tests be skipped or excluded | delivery can model zones the natural way immediately | the schema stops being tested; `ddl-auto=validate` no longer covers those entities; CI goes green on a schema it never applied. This is the worst of the options and is listed to be rejected explicitly |
| Keep a separate test-only variant of the spatial migrations | tests keep running with no Docker | the tested schema is not the real schema, which is the exact failure [ADR-0008](0008-real-postgresql-in-tests.md) rejected H2 for; two migration sets to keep in step by hand |
| Move every context test to Testcontainers with `postgis/postgis:17-3.5` now | one database everywhere; PostGIS available; GitHub runners have Docker | every test run needs a Docker daemon, which at least one developer does not have; pulls the container start cost into every build for a module that does not exist yet |
| Ban PostGIS types until the module that needs them moves its own tests to Testcontainers | the schema stays fully tested; no Docker requirement today; the cost is paid by the module that needs the feature, when it needs it | delivery zones must be modelled without spatial types until then; two test base classes once it happens |

## Decision

**No migration may use PostGIS types** — no `geometry`, no `geography`, no
`CREATE EXTENSION postgis`, no spatial index — until the `delivery` module has
moved its context tests to Testcontainers with the `postgis/postgis:17-3.5`
image.

This is a rule with a trigger, not an intention:

- A pull request adding a spatial type to a migration is rejected unless the same
  pull request adds the Testcontainers base class and CI runs on it.
- Until then, `delivery` models zones without PostGIS: a postcode or region table,
  a bounding box as four `NUMERIC` columns, or a polygon stored as JSON and
  evaluated in Java. Whichever is chosen, it is plain PostgreSQL.
- When delivery does need real spatial queries, that module's tests get a
  `PostgisIntegrationTest` base class using Testcontainers, and only those tests
  require Docker. GitHub Actions runners provide it; a developer without Docker
  can run the rest of the suite and will see those tests fail to start, which is
  visible rather than silent.
- The PostgreSQL major version must match: `postgis/postgis:17-3.5` against the
  embedded 17 and the compose database.

`docker-compose.yml` keeps the PostGIS image. It costs nothing, and swapping it
out now would only have to be swapped back.

## Reason

The value of [ADR-0008](0008-real-postgresql-in-tests.md) is that the tested
schema is the real schema. A migration that CI never applies removes that
guarantee for those tables and leaves `ddl-auto=validate` validating nothing
there. Allowing PostGIS today buys convenience for a module that does not exist,
at the price of the one property that makes the test suite trustworthy.

Moving everything to Testcontainers now would also solve it, and is probably
where this ends up. It is not worth doing before there is a single spatial column
to justify making Docker mandatory for every developer on every build.

Writing it as a rule with a named trigger matters because the alternative — "we
should be careful with PostGIS" — is how someone writes
`ALTER TABLE delivery_zone ADD COLUMN area geometry(Polygon, 4326)` in six
months and spends an afternoon finding out why CI broke.

## Consequences

**Positive**

- The test database keeps running every migration, so a schema change cannot
  reach `main` untested.
- No Docker requirement for the current suite.
- The mismatch is written down, so nobody rediscovers it through a red build.
- When PostGIS does arrive, the cost is scoped to one module.

**Negative**

- Delivery zones are modelled around the limitation until the move happens, and
  that model may have to be migrated afterwards.
- Radius and containment queries that PostGIS would do in SQL have to be done in
  Java, or approximated with a bounding box, until then.
- The local Docker database has an extension the application must not use, which
  is a trap for anyone who checks what the database supports instead of reading
  this ADR.
- After the move, two test base classes exist and a developer has to know which
  to extend.

**Revisit when**

- `delivery` needs real spatial queries. Then move that module's context tests to
  Testcontainers in the same pull request as the first spatial migration, and
  supersede this ADR.
- A PostGIS-enabled embedded provider becomes available and pinned to PostgreSQL
  17. That would remove the Docker requirement and the rule with it.
- Any other module needs spatial types, at which point the question is whether
  the whole suite should move to Testcontainers rather than a second base class.
