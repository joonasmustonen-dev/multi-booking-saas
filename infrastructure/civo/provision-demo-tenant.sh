#!/bin/sh
set -eu
cd "$(dirname "$0")"

admin_subject="${1:-${DEMO_ADMIN_SUBJECT:-}}"
admin_email="${2:-${DEMO_ADMIN_EMAIL:-}}"

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

if [ -n "$admin_subject" ] || [ -n "$admin_email" ]; then
  if [ -z "$admin_subject" ] || [ -z "$admin_email" ]; then
    echo "Both the Keycloak subject and email are required to seed the administrator." >&2
    exit 2
  fi

  docker compose exec -T postgres psql --set=ON_ERROR_STOP=1 \
    --set=admin_subject="$admin_subject" \
    --set=admin_email="$admin_email" \
    --username postgres --dbname platform_db <<'SQL'
INSERT INTO workspace_memberships (
  id,
  tenant_id,
  identity_subject,
  email,
  role,
  status,
  created_at,
  updated_at
)
SELECT
  gen_random_uuid(),
  id,
  :'admin_subject',
  lower(:'admin_email'),
  'TENANT_ADMIN',
  'ACTIVE',
  now(),
  now()
FROM tenants
WHERE slug = 'demo'
ON CONFLICT (tenant_id, identity_subject) DO UPDATE SET
  email = EXCLUDED.email,
  role = 'TENANT_ADMIN',
  status = 'ACTIVE',
  staff_member_id = NULL,
  removed_at = NULL,
  updated_at = now();
SQL
  echo "Seeded the initial demo workspace administrator."
else
  echo "Tenant provisioned without a membership. Pass KEYCLOAK_SUBJECT EMAIL before enabling membership enforcement."
fi

docker compose restart backend
