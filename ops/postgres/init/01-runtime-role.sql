-- Runtime database role: the application connects with DML only, never DDL.
--
-- Two roles, on purpose:
--   * the owner (POSTGRES_USER, "grocery") owns the schema and runs Flyway migrations
--   * "grocery_app" can read and write rows, and nothing else
--
-- So a SQL injection hole or a bug in application code cannot DROP a table, add a
-- column, or read another database: the connection it would use has no such right.
--
-- This file is pure SQL with no psql meta-commands, because it is also executed by
-- DatabaseRolePrivilegeTest against a real PostgreSQL, so the privileges below are
-- verified by the build instead of being assumed.
--
-- Docker runs it once, on first initialisation of an empty data volume
-- (/docker-entrypoint-initdb.d). Run it by hand on a managed database:
--   psql "$DB_URL" -f ops/postgres/init/01-runtime-role.sql

-- The role itself. LOGIN, but no password yet: 02-runtime-role-password.sh sets it
-- from the environment, so no password is ever written to a file in Git.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'grocery_app') THEN
        CREATE ROLE grocery_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
END $$;

-- May connect to this database, and to no other
DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO grocery_app', current_database());
END $$;

-- May see the schema, but not create in it
GRANT USAGE ON SCHEMA public TO grocery_app;

-- Rows in the tables that exist now (none on a fresh database; this file is re-runnable)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO grocery_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO grocery_app;

-- And in every table a future migration creates, without having to re-grant after
-- each deploy. Applies to objects created by the role running this file, which is
-- the same role Flyway uses.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO grocery_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO grocery_app;

-- Nobody creates objects in public except the owner. (PostgreSQL 15 and later already
-- default to this; stated explicitly so an older or restored database matches.)
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
