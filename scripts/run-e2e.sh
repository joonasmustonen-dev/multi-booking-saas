#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose_file="$repo_root/infrastructure/e2e/docker-compose.yml"
backend_log="$repo_root/e2e-backend.log"
compose_log="$repo_root/e2e-compose.log"
backend_pid=""

cleanup() {
    status=$?
    trap - EXIT INT TERM
    if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
        kill "$backend_pid" 2>/dev/null || true
        wait "$backend_pid" 2>/dev/null || true
    fi
    docker compose -f "$compose_file" logs --no-color > "$compose_log" 2>&1 || true
    docker compose -f "$compose_file" down --volumes --remove-orphans >/dev/null 2>&1 || true
    exit "$status"
}
trap cleanup EXIT INT TERM

cd "$repo_root"
docker compose -f "$compose_file" down --volumes --remove-orphans >/dev/null 2>&1 || true
docker compose -f "$compose_file" up -d --wait postgres keycloak

keycloak_ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
    if curl --fail --silent --max-time 2 \
        http://localhost:8081/realms/booking/.well-known/openid-configuration \
        >/dev/null; then
        keycloak_ready=true
        break
    fi
    sleep 2
done
if [[ "$keycloak_ready" != true ]]; then
    echo "Keycloak did not become ready within 180 seconds." >&2
    exit 1
fi

(cd backend && bash mvnw --batch-mode --no-transfer-progress -DskipTests package)
POSTGRES_CONTAINER=$(docker compose -f "$compose_file" ps -q postgres) \
    bash scripts/ci-bootstrap-db.sh

jar_files=(backend/target/booking-backend-*.jar)
java -jar "${jar_files[0]}" \
    --server.address=127.0.0.1 \
    --server.port=8080 \
    > "$backend_log" 2>&1 &
backend_pid=$!

backend_ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
    if ! kill -0 "$backend_pid" 2>/dev/null; then
        echo "Backend exited before becoming ready." >&2
        cat "$backend_log"
        exit 1
    fi
    if curl --fail --silent --max-time 2 http://127.0.0.1:8080/api/health >/dev/null; then
        backend_ready=true
        break
    fi
    sleep 2
done
if [[ "$backend_ready" != true ]]; then
    echo "Backend did not become ready within 180 seconds." >&2
    cat "$backend_log"
    exit 1
fi

postgres_container=$(docker compose -f "$compose_file" ps -q postgres)
docker exec -i "$postgres_container" psql \
    -U booking -d platform_db -v ON_ERROR_STOP=1 \
    < scripts/e2e-seed.sql

cd frontend
npm run test:e2e
