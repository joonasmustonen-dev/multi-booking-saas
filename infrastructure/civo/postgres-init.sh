#!/bin/sh
set -eu

psql --set=ON_ERROR_STOP=1 --username postgres --dbname postgres \
  --set=runtime_password="$DATABASE_RUNTIME_PASSWORD" \
  --set=migration_password="$DATABASE_MIGRATION_PASSWORD" \
  --set=keycloak_password="$KEYCLOAK_DATABASE_PASSWORD" <<'SQL'
CREATE ROLE booking_runtime LOGIN PASSWORD :'runtime_password';
CREATE ROLE booking_migration LOGIN PASSWORD :'migration_password';
CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE platform_db OWNER booking_migration;
CREATE DATABASE tenant_demo OWNER booking_migration;
CREATE DATABASE keycloak OWNER keycloak;
GRANT CONNECT ON DATABASE platform_db TO booking_runtime;
GRANT CONNECT ON DATABASE tenant_demo TO booking_runtime;
SQL

for database in platform_db tenant_demo; do
  psql --set=ON_ERROR_STOP=1 --username postgres --dbname "$database" <<'SQL'
GRANT USAGE, CREATE ON SCHEMA public TO booking_migration;
GRANT USAGE ON SCHEMA public TO booking_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE booking_migration IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO booking_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE booking_migration IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO booking_runtime;
SQL
done
