#!/usr/bin/env bash
set -euo pipefail

# Run from the repository root against the disposable Actions service container.
: "${POSTGRES_CONTAINER:?Set POSTGRES_CONTAINER to the CI PostgreSQL container ID}"
jar_files=(backend/target/booking-backend-*.jar)
if [[ ${#jar_files[@]} -ne 1 || ! -f "${jar_files[0]}" ]]; then
  echo 'Build the backend JAR before initializing CI databases.' >&2
  exit 1
fi

# Refuse an already-initialized platform database. This bootstrap is for fresh CI.
registry=$(docker exec "$POSTGRES_CONTAINER" psql -U booking -d platform_db \
  -At -v ON_ERROR_STOP=1 -c "SELECT to_regclass('public.tenants')")
if [[ -n "$registry" ]]; then
  echo 'CI bootstrap expects an empty platform database; refusing existing tenant data.' >&2
  exit 1
fi

# The first boot runs the normal platform Flyway migration with an empty registry.
# Keycloak is unnecessary here; JWT-enabled tests supply their own test decoder.
java -jar "${jar_files[0]}" \
  --app.security.jwt-enabled=false \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/platform_db \
  --spring.datasource.username=booking \
  --spring.datasource.password=booking \
  --server.address=127.0.0.1 \
  --server.port=18080 > ci-bootstrap.log 2>&1 &
bootstrap_pid=$!
cleanup() {
  if kill -0 "$bootstrap_pid" 2>/dev/null; then
    kill "$bootstrap_pid"
  fi
  wait "$bootstrap_pid" 2>/dev/null || true
}
trap cleanup EXIT

ready=false
last_http_status=unreachable
for ((attempt = 0; attempt < 60; attempt++)); do
  if ! kill -0 "$bootstrap_pid" 2>/dev/null; then
    echo 'Bootstrap application exited before becoming ready.' >&2
    cat ci-bootstrap.log
    exit 1
  fi
  last_http_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --max-time 2 http://127.0.0.1:18080/api/health) || last_http_status=unreachable
  if [[ "$last_http_status" == 200 ]]; then
    ready=true
    break
  fi
  sleep 2
done
if [[ "$ready" != true ]]; then
  echo 'Bootstrap application did not become ready within 120 seconds.' >&2
  echo "Last health HTTP status: $last_http_status" >&2
  cat ci-bootstrap.log
  exit 1
fi
cleanup
trap - EXIT

# Preserve the real Flyway history; do not manually create platform business tables.
docker exec -i "$POSTGRES_CONTAINER" psql -U booking -d platform_db \
  -v ON_ERROR_STOP=1 < scripts/setup-dev-tenants.sql
echo 'CI platform registry, tenant databases and routing markers initialized.'
# clean verify starts the application context and migrates both ACTIVE tenants.
