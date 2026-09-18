#!/bin/sh
# Sets the runtime role's password from the environment, so the password lives in
# .env (git-ignored) or a secrets manager, never in a committed SQL file.
#
# Docker's postgres entrypoint runs every file in /docker-entrypoint-initdb.d in
# name order, after 01-runtime-role.sql has created the role.
set -eu

: "${APP_DB_PASSWORD:?APP_DB_PASSWORD must be set for the grocery_app runtime role}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v password="$APP_DB_PASSWORD" <<'EOSQL'
ALTER ROLE grocery_app WITH PASSWORD :'password';
EOSQL
