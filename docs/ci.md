# CI setup and browser-test troubleshooting

GitHub Actions runs four independent concerns: backend verification, frontend checks, an authenticated browser lifecycle, and repository security checks. No repository secrets are needed for tests. All database and Keycloak credentials in the E2E files belong only to short-lived local containers.

## Browser lifecycle

`scripts/run-e2e.sh` performs the same sequence locally and in Actions:

1. Start isolated PostgreSQL 16 and Keycloak 26 containers from `infrastructure/e2e/docker-compose.yml`.
2. Import the dedicated `booking` realm and wait for its OpenID configuration endpoint.
3. Build and boot Spring Boot once to migrate the platform database, create the two test tenants, and then boot normally with JWT validation enabled.
4. Seed one customer, one resource-only service, recurring availability, and deterministic tenant settings into `tenant_a`.
5. Start Vite through Playwright and run Chromium against the real Keycloak authorization-code flow.
6. Stop the application and delete the isolated containers and volumes, even after a failure.

Keycloak startup imports `infrastructure/e2e/booking-realm.json`. Keep that exact `<realm-name>-realm.json` filename: Keycloak's directory importer uses the filename to identify the realm. The runner prints Keycloak logs immediately if the realm endpoint does not appear.

The tests cover login, creating a booking from generated availability, rescheduling it to another date, and cancelling it. The booking tests run serially because they intentionally exercise one lifecycle across multiple browser sessions.

## Run locally

Install Java 21, Node 24, Docker with Compose v2, and Chromium for Playwright:

```bash
cd frontend
npm ci
npx playwright install --with-deps chromium
cd ..
bash scripts/run-e2e.sh
```

Ports `5432`, `8080`, `8081`, and `5173` must be free. Stop the normal development Compose stack before running the isolated suite.

## Failure artifacts

The `browser-e2e-report` artifact contains the HTML report, retained traces/screenshots/videos, Spring Boot log, Compose logs, and bootstrap log. Download it from the failed workflow run. Open a retained trace with:

```bash
cd frontend
npx playwright show-trace test-results/<test-directory>/trace.zip
```

For a local failure, the same logs remain as `e2e-backend.log`, `e2e-compose.log`, and `ci-bootstrap.log`. These files are ignored by Git.

## Required checks

If branch protection is enabled, require these stable job names:

- Backend tests and build
- Frontend checks and build
- Keycloak browser end-to-end tests
- Scan committed secrets

Dependency review runs only on eligible pull requests. The workflow uses concurrency cancellation so a newer push supersedes an older run on the same branch.
