# ADR-0013: Least-privilege database roles for the application

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

Until now the application connected to PostgreSQL as `grocery`, the role that owns
the schema. That role can create, alter and drop every table, and Flyway needs
exactly that to run migrations ([ADR-0002](0002-postgresql-as-primary-database.md)).
Using it for request traffic as well means every request is served by a connection
that could destroy the database.

That matters because of what the platform will hold: 100 vendors and up to a
million customer accounts, plus orders, payouts and addresses. The realistic paths
to damage are ordinary ones, not exotic ones:

- a native query or `LIKE` filter built by string concatenation in a future module,
  where a parameter reaches the database as SQL;
- a migration or a maintenance script pointed at the wrong environment;
- a dependency with a remote-code-execution flaw, where the attacker inherits
  whatever the connection may do.

In each case the privileges on the connection decide whether the result is a bad
row or a lost table. Application code needs `SELECT`, `INSERT`, `UPDATE` and
`DELETE`, and never needs DDL: the schema only ever changes through a Flyway
migration, and Hibernate runs with `ddl-auto: validate`, so it issues no DDL either.

PostgreSQL grants privileges per table, and a table that does not exist yet cannot
be granted. Since every release adds tables, any solution has to cover future
tables without a manual step after each deploy, or it will quietly rot.

## Decision

Two roles.

- **`grocery`** owns the schema and runs Flyway migrations. Unchanged.
- **`grocery_app`** is what the running application connects as: `SELECT`,
  `INSERT`, `UPDATE`, `DELETE` and sequence use on `public`, and nothing else. No
  `CREATE`, no ownership, not superuser, cannot create roles or databases.

`ALTER DEFAULT PRIVILEGES IN SCHEMA public` grants those rights on every table the
owner creates from now on, so a new migration needs no follow-up grant.

The roles are created by `ops/postgres/init/01-runtime-role.sql`, which Docker runs
on first initialisation of the database volume, and which is applied by hand on a
managed database. The runtime password is set separately from `APP_DB_PASSWORD`
(`02-runtime-role-password.sh`), so no password is committed.

Spring is configured to use both: `spring.flyway.user` / `password` take the owner
credentials from `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD`, and the
DataSource takes the runtime ones. Where the migration variables are unset, both
fall back to the application credentials, so a single-role database (a plain local
PostgreSQL, the embedded server in tests) still works.

`DatabaseRolePrivilegeTest` executes the init script against a real PostgreSQL in
the build and asserts the resulting privileges from both sides: the runtime role
can write rows in a table created *after* the grants, and cannot `CREATE`, `DROP`
or `ALTER` a table.

## Alternatives considered

| Option | For | Against |
|--------|-----|---------|
| One role for everything (what we had) | simplest; one set of credentials | every request is served by a connection that can drop any table. The cheapest possible mitigation for the worst possible outcome is declined for convenience |
| Two roles, granted per table in each migration | explicit; visible in the migration | every migration must remember a `GRANT`, and the one that forgets fails in production, not in review. Rot is certain |
| A read-only role as well, for reporting | protects report queries from writing | nothing reports yet; would be a role with no user. Add it with the first reporting consumer |
| Row-level security for vendor isolation | isolation enforced by the database | a different problem (multi-tenant data separation) at a different layer; it will be decided with the vendor module. Not a reason to leave DDL rights on the request connection today |

## Consequences

- A compromised or buggy application path can corrupt rows, which optimistic
  locking, constraints and backups address; it cannot drop or alter the schema.
- Migrations get their own connection, separate from the Hikari pool. That turns
  out to be desirable on its own: the pool sets `statement_timeout=10s`, which is
  right for a request and wrong for a migration that builds an index on a large
  table.
- A database whose volume predates the init script has no `grocery_app` role, and
  the application fails to authenticate against it. There is no production data
  yet, so the documented fix is `docker compose down -v`; on a database with data,
  the scripts are applied by hand. Recorded in
  [database-operations.md](../../development/database-operations.md).
- Any table created outside a migration (by hand in `psql`, as the owner) is *not*
  usable by the application until it is granted. That is intended: it makes the
  "schema changes only through migrations" rule enforced rather than advisory.
- Two more credentials to manage per environment. In production both come from the
  secrets manager, and only the deploy step needs the owner's.
