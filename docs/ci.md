# GitHub Actions CI

The workflow is `.github/workflows/ci.yml`. It runs on every branch push, pull request, and manual dispatch. Backend and frontend jobs run independently on Ubuntu 24.04. New runs cancel older runs for the same ref.

## Backend tests and build

1. Check out source and install Temurin Java 21, caching Maven dependencies.
2. Start a fresh PostgreSQL 16 service with the public development-only booking credentials and platform_db. Wait for its health check.
3. Build the application JAR with tests skipped for this bootstrap build only.
4. Run scripts/ci-bootstrap-db.sh. It refuses an existing platform tenant registry, boots the application on loopback port 18080 to apply the normal platform Flyway migration, waits for health, and stops the process.
5. Execute scripts/setup-dev-tenants.sql inside the PostgreSQL container to create tenant_a/tenant_b, ACTIVE tenant registry rows, and routing-test markers.
6. Run `bash mvnw --batch-mode --no-transfer-progress clean verify`. The test application contexts migrate both tenants, run the full test suite, and package the backend.

Bootstrapping through the application preserves real Flyway history. Manually creating the platform tenants table before Spring startup would leave a nonempty schema without that history and can prevent migration.

The service exposes localhost:5432 to the runner, matching the current routing tests and tenant datasource port. All databases are disposable and belong to that Actions job. Nothing connects to a developer computer or production database.

The bootstrap disables JWT only for its temporary application process. It does not change repository configuration or globally disable JWT in the test run. Tests that enable JWT provide their own decoder and mocked tokens. Real Keycloak login, token issuance, realm configuration, email and browser behavior are not covered by this pipeline.

## Frontend checks and build

Install Node 24, cache npm downloads using package-lock.json, then run:

```bash
npm ci
npm run test:assignments
npm run lint
npm run build
```

The checks use mocked API requests and server rendering; they require neither PostgreSQL nor Keycloak. TypeScript compilation is part of npm run build. npm ci installs the committed lockfile rather than updating dependencies.

## Results and artifacts

Open the repository's Actions tab, select CI, then select a run. A failing command fails its job. Uploaded artifacts are retained for seven days:

- backend-test-reports: Surefire/Failsafe reports when present plus ci-bootstrap.log, including on failure.
- backend-jar: packaged application after successful backend verification.
- frontend-dist: compiled frontend after successful frontend verification.

Artifacts are downloadable build results, not deployments. Frontend builds still contain the project's current development URL constants until those are externalized.

The workflow requires no custom GitHub secrets, has read-only repository permissions, and uses pull_request rather than pull_request_target. It does not publish packages, push commits, or deploy. Official actions use major-version tags; a future maintenance improvement is pinning them to reviewed commit SHAs with automated updates.

## Enable and require checks

Commit the workflow, bootstrap script, existing tenant setup SQL, and documentation with the application source, Maven wrapper, and frontend lockfile. Push to GitHub. CI starts automatically; workflow_dispatch is also available once the workflow is on the default branch.

After the first successful run, optionally configure a main branch ruleset requiring **Backend tests and build** and **Frontend checks and build** before merging. These rules are repository settings and are not configured by this workflow file.

Git Bash, from the repository root:

```bash
git add -- .github/workflows/ci.yml scripts/ci-bootstrap-db.sh scripts/setup-dev-tenants.sql README.md docs/ci.md
git diff --cached --stat
git commit -m "Add GitHub Actions backend and frontend CI"
git push
```

These commands assume the application source and lockfile have already been committed using the publishing guide. If they have not, include those changes in the same commit before pushing. The workflow invokes scripts with bash, so Windows executable-bit defaults do not prevent execution; .gitattributes ensures shell scripts use LF.

## Troubleshooting

- **Bootstrap failure:** download ci-bootstrap.log. Check PostgreSQL readiness, application startup, and platform migration errors.
- **Missing tenant or routing markers:** inspect the Initialize fresh test databases step; do not remove the seed SQL or bypass its error checks.
- **Dependency download failure:** retry a transient network failure; check Maven Central/npm availability if failures repeat.
- **Frontend checks fail:** reproduce with Node 24 and npm ci. Do not replace npm ci with npm install to mask a lockfile mismatch.
- **Wrapper fails:** keep backend/.mvn/wrapper/maven-wrapper.properties committed. The wrapper downloads Maven on first use.
- **Job timeout:** inspect logs before increasing the limit. Backend has 20 minutes; frontend has 15 minutes; bootstrap health polling has a bounded wait.

Local application checks can be run with the README commands. The CI bootstrap script is intentionally for a fresh disposable Actions database and should not be run against an existing developer or production database. The first GitHub run is the final validation of Linux runner setup, container startup, dependency downloads and artifact uploads.
