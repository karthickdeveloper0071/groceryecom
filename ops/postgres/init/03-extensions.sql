-- Extensions the platform relies on for operations.
--
-- pg_stat_statements is how a slow endpoint is traced back to the statement behind it:
-- it records every normalised query with call count and total/mean time. Without it,
-- tuning turns into guessing, and indexes get added blindly.
-- It needs shared_preload_libraries=pg_stat_statements, which docker-compose.yml passes
-- to the server, so this file cannot be executed against a server started without it.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Note: the PostGIS extension is created by the postgis/postgis image itself.
-- Migrations must not use PostGIS types yet; see docs/architecture/adr/0012-postgis-in-tests.md.
