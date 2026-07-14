#!/bin/bash
# Runs once, on first initialization of an empty Postgres data directory. Gives Keycloak its own
# database and role in the same Postgres instance the app uses, so the VM runs one database server.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER keycloak WITH PASSWORD '${KEYCLOAK_DB_PASSWORD}';
	CREATE DATABASE keycloak OWNER keycloak;
EOSQL
