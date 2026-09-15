#!/bin/sh
set -eu
cd "$(dirname "$0")"

docker compose exec -T postgres psql --set=ON_ERROR_STOP=1 \
  --username postgres --dbname platform_db <<'SQL'
INSERT INTO tenants (id, slug, name, database_name, status, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'demo', 'Demo workspace', 'tenant_demo', 'ACTIVE', now())
ON CONFLICT (slug) DO UPDATE SET
  name = EXCLUDED.name,
  database_name = EXCLUDED.database_name,
  status = EXCLUDED.status;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO booking_runtime;
SQL

docker compose restart backend
