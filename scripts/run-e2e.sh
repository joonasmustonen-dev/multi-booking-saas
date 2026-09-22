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
echo "[e2e] Starting disposable PostgreSQL and Keycloak containers..."
docker compose -f "$compose_file" down --volumes --remove-orphans >/dev/null 2>&1 || true
docker compose -f "$compose_file" up -d --wait postgres keycloak

echo "[e2e] Waiting for the imported booking realm..."
keycloak_ready=false
last_keycloak_status=unreachable
for ((attempt = 1; attempt <= 60; attempt++)); do
    last_keycloak_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
        --max-time 2 \
        http://localhost:8081/realms/booking/.well-known/openid-configuration
    ) || last_keycloak_status=unreachable
    if [[ "$last_keycloak_status" == 200 ]]; then
        keycloak_ready=true
        break
    fi
    if ((attempt % 10 == 0)); then
        echo "[e2e] Still waiting for realm (attempt $attempt/60, HTTP $last_keycloak_status)..."
    fi
    sleep 2
done
if [[ "$keycloak_ready" != true ]]; then
    echo "Keycloak realm did not become ready within 120 seconds (last HTTP status: $last_keycloak_status)." >&2
    echo "Keycloak logs:" >&2
    docker compose -f "$compose_file" logs --no-color keycloak >&2 || true
    exit 1
fi
echo "[e2e] Keycloak realm is ready."

echo "[e2e] Resolving the imported administrator identity..."
e2e_token_response=$(curl --fail --silent \
    --request POST \
    --data-urlencode 'client_id=booking-frontend' \
    --data-urlencode 'username=e2e-admin' \
    --data-urlencode 'password=playwright-only-password' \
    --data-urlencode 'grant_type=password' \
    http://localhost:8081/realms/booking/protocol/openid-connect/token)
e2e_admin_subject=$(printf '%s' "$e2e_token_response" | node -e '
const input = JSON.parse(require("fs").readFileSync(0, "utf8"));
if (!input.access_token) process.exit(1);
const segments = input.access_token.split(".");
if (segments.length !== 3) process.exit(1);
const claims = JSON.parse(Buffer.from(segments[1], "base64url").toString());
if (!claims.sub) process.exit(1);
process.stdout.write(claims.sub);
')
echo "[e2e] Imported administrator identity resolved."

echo "[e2e] Building the Spring Boot application..."
(cd backend && bash mvnw --batch-mode --no-transfer-progress -DskipTests package)
echo "[e2e] Creating and migrating disposable tenant databases..."
POSTGRES_CONTAINER=$(docker compose -f "$compose_file" ps -q postgres) \
    bash scripts/ci-bootstrap-db.sh

echo "[e2e] Starting the JWT-enabled backend..."
jar_files=(backend/target/booking-backend-*.jar)
java -jar "${jar_files[0]}" \
    --server.address=127.0.0.1 \
    --server.port=8080 \
    --app.security.membership-enforcement=true \
    > "$backend_log" 2>&1 &
backend_pid=$!

backend_ready=false
for ((attempt = 1; attempt <= 90; attempt++)); do
    if ! kill -0 "$backend_pid" 2>/dev/null; then
        echo "Backend exited before becoming ready." >&2
        cat "$backend_log"
        exit 1
    fi
    if curl --fail --silent --max-time 2 http://127.0.0.1:8080/api/health >/dev/null; then
        backend_ready=true
        break
    fi
    if ((attempt % 10 == 0)); then
        echo "[e2e] Still waiting for backend (attempt $attempt/90)..."
    fi
    sleep 2
done
if [[ "$backend_ready" != true ]]; then
    echo "Backend did not become ready within 180 seconds." >&2
    cat "$backend_log"
    exit 1
fi
echo "[e2e] Backend is ready."

echo "[e2e] Loading deterministic booking fixtures..."
postgres_container=$(docker compose -f "$compose_file" ps -q postgres)
docker exec -i "$postgres_container" psql \
    -U booking -d platform_db -v ON_ERROR_STOP=1 \
    --set=e2e_admin_subject="$e2e_admin_subject" \
    < scripts/e2e-seed.sql

echo "[e2e] Running Playwright in Chromium..."
cd frontend
npm run test:e2e
