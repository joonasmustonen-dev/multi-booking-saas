# Multi Booking SaaS

A multi-tenant booking workspace built with Spring Boot, React, PostgreSQL, and Keycloak. Services describe the staff, locations, and equipment needed for a booking. Availability generates complete assignment combinations, and appointments validate those combinations again when saved.

This repository is under active development. The included Docker Compose configuration is for local development; it is not a production deployment.

## Features

- **Customers:** bounded name/email/phone search, preferred staff, paginated booking history, completed-visit summaries, and attendance context in booking and calendar details. [Customer guide](docs/customer-operations.md)

- **Services:** duration, pricing, description, independent eligible staff/location/resource lists, and required/optional/forbidden assignment rules.
- **Staff:** contact information, activation, archiving, location assignments, free agents, recurring hours, time off, and dated weekly rotas with split shifts and copying from another week.
- **Locations:** contact/address details, opening hours, closures, and staff assignments.
- **Resources:** equipment and other bookable things, descriptions, activation, and their own hours and exceptions.
- **Appointments:** create, reschedule, cancel, update status, and view styled appointment details and customer contact information.
- **Calendar:** weekly view with staff, location, service, and status filters.
- **Dashboard:** booking summaries and staff information.
- **Settings:** business details, timezone, currency, booking notice/horizon, slot interval, calendar hours/week start, and initial appointment status.
- **Tenant isolation:** a platform tenant registry plus a separate PostgreSQL database for each tenant, selected from the authenticated JWT.

## Stack and prerequisites

| Component | Technology / local requirement |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.1, Maven wrapper |
| Frontend | React 19, TypeScript 6, Vite 8, React Query, React Router |
| Node | Node.js 24 LTS recommended; the frontend checks use `node:module` registerHooks |
| Database | PostgreSQL 16 with Flyway migrations |
| Authentication | Keycloak 26.7.3 in the development Compose file; browser Authorization Code flow with PKCE S256 |
| Infrastructure | Docker Desktop / Docker Engine with Compose v2 |

Install Git, Java 21, Node.js 24, and Docker. The Maven wrapper downloads Maven on first use, so a separate Maven installation is optional. Commands below use Git Bash on Windows; on Linux/macOS use the same commands with your clone path.

## Repository structure

```text
backend/
  src/main/java/com/example/booking/
    config/                 Security, persistence and routing configuration
    tenant/                 Platform registry and tenant database routing
    tenantdata/             Booking domain and REST APIs
  src/main/resources/
    application.yml         Local defaults
    db/migration/           Platform migrations
    db/tenant/              Per-tenant migrations
  src/test/                 Unit and integration tests
frontend/
  src/auth/                 Keycloak browser integration
  src/api/                  Authenticated API client
  src/components/           Shared controls and interaction styles
  src/features/             Services, staff, locations, resources, bookings, settings
  tests/assignmentFlows.mjs  API, helper and server-render checks
infrastructure/compose/     Development PostgreSQL and Keycloak
scripts/                    Local setup helpers
docs/                       Project documentation
```

## Run locally

### 1. Clone and start infrastructure

```bash
git clone https://github.com/joonasmustonen-dev/multi-booking-saas.git
cd multi-booking-saas
docker compose -f infrastructure/compose/docker-compose.dev.yml up -d
docker compose -f infrastructure/compose/docker-compose.dev.yml ps
```

| Service | Address | Development credentials |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | user/password `booking` / `booking` |
| Keycloak Admin Console | `http://localhost:8081` | `admin` / `admin` |
| Backend | `http://localhost:8080` | Bearer access token for protected APIs |
| Frontend | `http://localhost:5173` | A Keycloak user configured below |

These credentials are deliberately public local defaults. Never reuse them in a deployed environment. Compose stores database and Keycloak state in Docker volumes; ordinary `down` preserves those volumes.

### 2. Initialize the platform database

Run the backend once so Flyway creates the platform tenant registry:

```bash
cd backend
./mvnw spring-boot:run
```

Wait for startup to finish, then stop with Ctrl+C. Return to the repository root and create the demo tenants:

```bash
cd ..
docker compose -f infrastructure/compose/docker-compose.dev.yml exec -T postgres \
  psql -U booking -d platform_db -v ON_ERROR_STOP=1 < scripts/setup-dev-tenants.sql
```

The script creates `tenant_a` and `tenant_b`, adds `tenant-a` and `tenant-b` to the registry, and adds markers used by the routing integration tests. It preserves existing databases and registry rows. Run it only against the development Compose database.

Restart the backend after running it. Startup migrates every ACTIVE tenant using `db/tenant`; business tables are created by Flyway, not by the setup script. A new tenant needs both a PostgreSQL database and an ACTIVE registry row. Self-service tenant provisioning is not implemented.

### 3. Configure Keycloak

The repository currently does not contain a realm export. A fresh Keycloak installation must be configured manually:

1. Sign in to the Admin Console and create a realm named **booking**.
2. Create an OpenID Connect client with ID **booking-frontend**.
3. Disable client authentication for this public browser client. Enable Standard Flow; use PKCE **S256**. A browser client must not contain a client secret.
4. Set Valid Redirect URIs to `http://localhost:5173/*`, Web Origins to `http://localhost:5173`, and Valid Post Logout Redirect URIs to `http://localhost:5173/*`.
5. Create realm roles **TENANT_ADMIN**, **STAFF**, and **CUSTOMER**. The backend reads `realm_access.roles` with these exact names.
6. Create a development user and set a password. Assign **TENANT_ADMIN** for full workspace management.
7. Add a user attribute `tenant_id` with value `tenant-a`. Depending on the realm user-profile settings, define this attribute first. End users must not be allowed to edit their tenant assignment.
8. Add a User Attribute protocol mapper to the client's dedicated client scope: User Attribute `tenant_id`, Token Claim Name `tenant_id`, JSON type String, Add to access token enabled. Ensure the realm role mapper also includes roles in the access token.
9. Verify the user's access token has `tenant_id: "tenant-a"` and the appropriate `realm_access.roles`. Do not paste real tokens into issues or documentation.

Use `tenant-b` for a user in the second demo workspace. Adding a Staff record in the application creates a scheduling record; it does not create a Keycloak login account.

### 4. Start the application

Backend terminal, from the repository root:

```bash
cd backend
./mvnw spring-boot:run
```

Frontend terminal, from the repository root:

```bash
cd frontend
npm ci
npm run dev -- --host localhost --port 5173 --strictPort
```

Open `http://localhost:5173`. The frontend redirects to Keycloak for login and attaches a refreshed access token to API requests. Use `localhost` consistently: `127.0.0.1` is a different origin for redirects and CORS.

To create a usable first booking, add staff/locations/resources, configure their hours, define a service's assignment requirements and eligible owners, then create a customer and appointment.

## Configuration

Backend defaults are in `backend/src/main/resources/application.yml`. Spring Boot can override them with environment variables:

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | Platform PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Platform database credentials |
| `APP_TENANT_DATABASE_HOST` | Tenant database host |
| `APP_TENANT_DATABASE_USERNAME` / `APP_TENANT_DATABASE_PASSWORD` | Tenant database credentials |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Keycloak realm issuer |
| `APP_SECURITY_JWT_ENABLED` | JWT authentication toggle; keep enabled for application use |
| `SERVER_PORT` | Backend HTTP port |

**Current configuration limits:** tenant routing currently hardcodes PostgreSQL port 5432, even though application.yml includes a tenant port property. Frontend API URL (`frontend/src/api/apiClient.ts`), Keycloak settings (`frontend/src/auth/keycloak.ts`), and backend CORS allowed origins (`SecurityConfig.java`) are currently source constants. They must be changed or externalized before deployment; adding a frontend `.env` alone does not change them.

Local `.env` files, credentials, keys, build output, and editor settings are excluded from Git. Dependency lockfiles and Flyway SQL migrations belong in Git. Do not edit a migration already applied to a shared database; add a new versioned migration instead.

## Booking and availability behavior

Each service specifies REQUIRED, OPTIONAL, or FORBIDDEN for staff, location, and resource. At least one category must be required. Eligible owners and requirement rules are separate: omitting a required category must not produce an incomplete booking.

Availability intersects the schedules of every selected owner and excludes conflicting appointments. Blocked exceptions override recurring hours and extra availability. Restricted staff can work only at assigned locations; free agents can work at any service-eligible location. Service eligibility still applies to free agents.

Appointments validate the service's exact assignment combination and availability at creation/rescheduling. Rescheduling excludes the appointment itself when calculating available slots. PostgreSQL exclusion constraints provide final protection against overlapping active bookings for the same staff, location, or resource. Cancellation frees capacity.

Dated staff rotas replace recurring hours for one Monday-based week. Resetting a week restores recurring rules while preserving independent time-off exceptions. Location management emphasizes regular opening hours and closures; resource management supports its own recurring hours and exceptions.

## REST API overview

Protected requests use `Authorization: Bearer <access-token>`. Tenant-scoped APIs require the token's `tenant_id` claim. Authorization differs by endpoint; management APIs generally require TENANT_ADMIN or STAFF, while settings updates require TENANT_ADMIN. Check controller method annotations for exact permissions.

| Route | Purpose |
| --- | --- |
| `GET /api/health` | Public application health endpoint |
| `GET /api/platform/tenants` | Platform registry listing; authenticated |
| `/api/v1/customers` | Customer management |
| `/api/v1/services` | Service definitions and assignment eligibility |
| `/api/v1/staff` | Staff contacts, assignments, activation and archiving |
| `/api/v1/locations` | Location management and activation |
| `/api/v1/resources` | Resource management and activation |
| `GET /api/v1/availability` | Complete available booking combinations |
| `/api/v1/{staff|locations|resources}/{ownerId}/availability/rules` | Recurring owner schedules |
| `/api/v1/{staff|locations|resources}/{ownerId}/availability/exceptions` | Owner closures/time off/extra availability |
| `/api/v1/staff/{id}/schedule?weekStart=YYYY-MM-DD` | GET/PUT dated weekly rota; DELETE resets it |
| `/api/v1/appointments` | Appointment creation, listing and details |
| `GET /api/v1/appointments/calendar` | Calendar data with filters |
| `POST /api/v1/appointments/{id}/reschedule` | Reschedule with validation |
| `GET /api/v1/appointments/{id}/availability` | Available combinations excluding that appointment |
| `POST /api/v1/appointments/{id}/cancel` | Cancel a booking |
| `PATCH /api/v1/appointments/{id}/status` | Update booking status |
| `GET /api/v1/dashboard/summary` | Workspace dashboard |
| `/api/v1/settings` | Read/update tenant settings |

Example availability query (substitute a service UUID and use the tenant timezone's desired dates):

```text
/api/v1/availability?serviceId=<uuid>&from=2026-10-05&to=2026-10-11
```

Optional `staffId`, `locationId`, and `resourceId` filters restrict the combinations. A slot includes the actual assignment IDs; preserve the complete combination when creating or rescheduling a booking rather than identifying a slot only by its time.

## Tests and builds

Backend tests require the local PostgreSQL instance, both demo tenants, their registry rows, and routing markers. Tests use mocked JWTs where appropriate, but this is not a database-free suite. Run against development databases, never production. Startup can apply migrations.

```bash
cd backend
./mvnw clean test
./mvnw clean package
```

Frontend checks:

```bash
cd frontend
npm ci
npm run test:assignments
npm run lint
npm run build
```

The frontend checks cover API contracts, assignment identity, date helpers, filtering helpers, and server-rendered components. They are not browser end-to-end tests. Recent development verification passed 88 backend tests and 58 frontend checks; rerun these commands on your own checkout before merging or deploying.

Build output is `backend/target/booking-backend-0.0.1-SNAPSHOT.jar` and `frontend/dist/`. Serve the frontend through a web server with SPA fallback for client routes. `vite preview` is useful for local build inspection and is not the production hosting setup.

## GitHub Actions CI

The workflow in .github/workflows/ci.yml runs on pushes, pull requests, and manual dispatch. It runs Java 21 backend tests/builds against disposable PostgreSQL 16 databases and Node 24 frontend checks, lint and build. It automatically initializes the platform registry and both test tenants; your local services do not need to be running. No custom GitHub secrets are required.

Test reports, the backend JAR and frontend build are uploaded as short-lived artifacts. Real Keycloak login and browser end-to-end tests are not included. This workflow validates builds and tests; it does not deploy the application. See [CI setup and troubleshooting](docs/ci.md) for bootstrap details and required-check setup.

## Troubleshooting

| Symptom | Check / action |
| --- | --- |
| PostgreSQL connection refused | Start Compose; check port 5432 and container logs |
| Unknown tenant / database missing | Run local tenant setup after platform initialization, then restart backend |
| Routing marker tests fail | Confirm both routing_test_data tables have the expected A/B markers |
| Login redirect error | Check realm/client IDs and exact frontend redirect origin |
| API 401 | Check issuer URL, token expiry, and whether authentication is enabled |
| API 403 / missing tenant claim | Check realm roles and tenant_id access-token mapper |
| No booking slots | Check requirements, eligibility, owner activity, hours, closures, location assignments, booking notice/horizon and existing appointments |
| NoSuchFieldError after edits | Stop competing IDE builds and restart after `./mvnw clean test`; stale target classes can cause this |
| Vite cannot spawn config bundler in a restricted environment | Try `npx tsc -b` followed by `npx vite build --configLoader native` |
| LF/CRLF warning in Git Bash | Review .gitattributes; shell scripts must use LF. This warning alone is not a failed commit |

Infrastructure logs and shutdown:

```bash
docker compose -f infrastructure/compose/docker-compose.dev.yml logs --tail=100
docker compose -f infrastructure/compose/docker-compose.dev.yml down
```

## Before production

Prepare a dedicated production deployment rather than exposing the development Compose stack:

- Run Keycloak in production mode with HTTPS, a proper database, restricted administrator access, secure redirects, email delivery and backups.
- Replace development credentials, use a secret store, and externalize frontend URLs and backend CORS configuration.
- Verify issuer, role, audience and tenant authorization policies. Review the platform tenant listing's authorization before exposing it publicly.
- Plan tenant provisioning and migrations, database connection capacity, backup/restore procedures, and monitoring.
- Add deployment automation and browser end-to-end coverage for login, booking, cancellation and rescheduling.
- Configure frontend SPA hosting, API HTTPS, structured logs and health monitoring.
- Review customer data handling and retention requirements, and select a license before distributing the project for reuse.

The current repository does not provide a production Compose stack, automated Keycloak realm provisioning, or deployment automation. GitHub Actions CI is included; see the CI section above. GitHub source hosting does not deploy the application or transfer existing Docker/database data.

## Calendar and management refinements

The calendar offers Week, Day and Agenda views, adaptive time labels and selectable groups for dense bookings. Booking details keep their header and actions visible, and customer details can open a prefilled appointment. Management pages offer searchable, paginated lists and optional cards. Recurring hours use day buttons and whole-week saving across staff, locations and resources. See [visual refinements](docs/visual-refinements.md) for the API contract and verification details.

Booking availability includes explicit loading, empty/error and retry states, and the dashboard appointment shortcut opens the editor directly. Duplicate staff/location names receive identifying labels. See [functional report follow-up](docs/functional-report-followup.md) for validation and remaining browser checks.
