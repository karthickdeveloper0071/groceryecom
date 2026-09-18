# Database operations

Everything about running PostgreSQL for this project: starting it, the two roles,
migrations, finding a slow query, backups, and getting out of trouble.

Related: [database standard](../engineering/database-standard.md) (how to write schema
and queries), [deployment](deployment.md) (Docker and environments).

## Start it

```bash
docker compose up -d
```

That starts PostgreSQL 17 (PostGIS image) and Redis. The database is then on
`localhost:5432`, database `grocery_ecom`.

First start of an **empty** data volume also runs `ops/postgres/init`:

| File | What it does |
|------|--------------|
| `01-runtime-role.sql` | Creates the `grocery_app` role and grants it rows-only access |
| `02-runtime-role-password.sh` | Sets that role's password from `APP_DB_PASSWORD` |
| `03-extensions.sql` | Creates `pg_stat_statements` |

These run **once per volume**, never again. If the database volume was created before
these files existed, the `grocery_app` role does not exist and the API cannot
authenticate. There is no production data yet, so the fix is to start clean:

```bash
docker compose down -v && docker compose up -d
```

`-v` deletes the database volume. On a database that does hold data, run the scripts by
hand instead — see [Applying the init scripts to an existing database](#applying-the-init-scripts-to-an-existing-database).

## Two roles, and why

| Role | Used by | May |
|------|---------|-----|
| `grocery` (owner) | Flyway migrations | everything: create, alter and drop tables |
| `grocery_app` | the running API | `SELECT`, `INSERT`, `UPDATE`, `DELETE` on rows, nothing else |

The API never connects with a role that can change the schema. So a SQL injection hole,
a wrong native query or a mistaken piece of code cannot drop a table or reach another
database: the connection has no such right. The split costs nothing at runtime.

Wiring: `docker-compose.yml` passes `DB_USERNAME=grocery_app` for the API and
`DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD` for the owner, and `application.yml`
hands the migration credentials to Flyway. Where the migration credentials are unset,
both fall back to the application ones, so a single-role database still works.

`ALTER DEFAULT PRIVILEGES` in the init script means every table a **future** migration
creates is readable and writable by `grocery_app` automatically. Nothing has to be
re-granted after a deploy.

`DatabaseRolePrivilegeTest` runs `01-runtime-role.sql` against a real PostgreSQL during
the build and checks exactly this: the runtime role can write rows in a table created
after the grants, and cannot `CREATE`, `DROP` or `ALTER` a table. Weakening the init
script fails the build.

## Migrations

Flyway owns the schema. Hibernate only validates against it (`ddl-auto: validate`), so a
mismatch between an entity and the schema fails at startup instead of at the first query.

- Files: `src/main/resources/db/migration/V<n>__<module>_<change>.sql`
- They run automatically when the application starts
- A migration that has been applied is **never edited**; add a new one
- Rules for writing them (nullable first, no long locks, backwards compatible) are in the
  [database standard](../engineering/database-standard.md)

What has been applied:

```bash
docker compose exec postgres psql -U grocery -d grocery_ecom -c "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank"
```

Migrations are exercised on every build: the tests start a real PostgreSQL 17 and apply
them from empty, so a migration that does not apply cleanly cannot reach `main`.

## A psql shell

```bash
docker compose exec postgres psql -U grocery -d grocery_ecom
```

Useful once inside: `\dt` tables, `\d+ users` one table, `\di` indexes, `\q` quit.

As the API sees it, to settle a privilege question:

```bash
docker compose exec postgres psql -U grocery_app -d grocery_ecom
```

## Finding a slow query

Do this before adding an index. `pg_stat_statements` records every statement with its
call count and time, so the answer comes from measurement rather than a guess.

Slowest statements by total time:

```sql
SELECT calls,
       round(mean_exec_time::numeric, 2) AS avg_ms,
       round(total_exec_time::numeric, 2) AS total_ms,
       rows,
       query
  FROM pg_stat_statements
 ORDER BY total_exec_time DESC
 LIMIT 20;
```

Then read the plan of the one that matters:

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT ...;
```

A `Seq Scan` over a large table inside a request is the usual finding. Add the index the
plan asks for, measure again, and put both numbers in the pull request.

Start a fresh measurement window with `SELECT pg_stat_statements_reset();`.

Other things worth knowing:

```sql
-- Currently running queries, longest first
SELECT pid, now() - query_start AS running, state, left(query, 120)
  FROM pg_stat_activity WHERE state <> 'idle' ORDER BY running DESC;

-- Who is blocking whom
SELECT pid, pg_blocking_pids(pid), left(query, 120)
  FROM pg_stat_activity WHERE cardinality(pg_blocking_pids(pid)) > 0;

-- Table and index sizes
SELECT relname, pg_size_pretty(pg_total_relation_size(relid))
  FROM pg_catalog.pg_statio_user_tables ORDER BY pg_total_relation_size(relid) DESC;

-- Indexes nobody uses (they still cost write time)
SELECT relname, indexrelname, idx_scan
  FROM pg_stat_user_indexes WHERE idx_scan = 0 ORDER BY relname;
```

The container also logs any statement slower than 500ms, plus lock waits and
checkpoints: `docker compose logs postgres`.

## Server settings

`docker-compose.yml` passes tuned settings, because the defaults assume a very small
machine (128MB of cache, no query statistics) and hide performance problems until
production. The ones that matter:

| Setting | Value | Why |
|---------|-------|-----|
| `max_connections` | 200 | instances x `DB_POOL_SIZE` must stay below it: 8 x 20 = 160, plus room for migrations and `psql` |
| `shared_buffers` | 512MB | about 25% of the container's memory limit |
| `effective_cache_size` | 1536MB | the planner's view of total cache, including the OS page cache |
| `random_page_cost` | 1.1 | SSD; the 4.0 default makes the planner avoid indexes it should use |
| `pg_stat_statements` | preloaded, `track=all` | the slow-query section above |
| `log_min_duration_statement` | 500ms | slow statements land in the container log |

Production uses a managed PostgreSQL, where these are set on the instance instead. Keep
them in step, especially `max_connections` against the pool size.

Collation is deliberately left at the image default: it decides `ORDER BY` results and
which indexes `LIKE` can use, so it must match the managed database rather than a value
invented locally. Compare `SHOW lc_collate` on both before the first release.

## Backup and restore

Locally, before experimenting on a database you care about:

```bash
docker compose exec -T postgres pg_dump -U grocery -d grocery_ecom -Fc > backup.dump
```

```bash
docker compose exec -T postgres pg_restore -U grocery -d grocery_ecom --clean --if-exists < backup.dump
```

Production is a managed PostgreSQL: use its automated backups and point-in-time
recovery, and **test a restore** before the first real customer. A backup nobody has
restored is not a backup.

## Applying the init scripts to an existing database

On a managed database, or one that already holds data, the Docker init hook never runs.
Apply the same files by hand, connected as a role that may create roles:

```bash
psql "$DB_URL" -f ops/postgres/init/01-runtime-role.sql
```

```bash
psql "$DB_URL" -c "ALTER ROLE grocery_app WITH PASSWORD 'from-your-secrets-manager'"
```

```bash
psql "$DB_URL" -f ops/postgres/init/03-extensions.sql
```

Then point `DB_USERNAME` / `DB_PASSWORD` at `grocery_app`, and `DB_MIGRATION_USERNAME` /
`DB_MIGRATION_PASSWORD` at the owner. Tables that existed before the script ran are
covered by its `GRANT ... ON ALL TABLES` statements, and the script is safe to re-run.

## When something is wrong

| Symptom | Cause and fix |
|---------|---------------|
| `FATAL: password authentication failed for user "grocery_app"` | The data volume predates `ops/postgres/init`. `docker compose down -v && docker compose up -d`, or apply the scripts by hand as above. |
| `permission denied for table ...` from the API | A table was created outside a migration, or the init script was changed. Grant it to `grocery_app`, or re-run the init script. |
| `Schema-validation: missing table [x]` at startup | An entity has no migration. Write the migration; never switch `ddl-auto` away from `validate`. |
| `Validate failed: Migration checksum mismatch` | An applied migration file was edited. Restore it and add a new migration instead. |
| Port 5432 already in use | A PostgreSQL service is running on the machine. Stop it, or change the published port in `docker-compose.yml`. |
| `ERROR: canceling statement due to statement timeout` | A request query took more than 10s (`statement_timeout` on the pool). Fix the query; do not raise the global timeout. Migrations are unaffected: Flyway has its own connection, without that timeout. |
| Tests cannot start a database | They use an embedded PostgreSQL, not Docker. Check that the temp directory is writable and that no antivirus blocks the extracted binaries. |
