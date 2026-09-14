# Visual refinements

Applied directly to F:/multi-booking-saas.

## Calendar and appointments

- Week, Day and Agenda views provide alternatives for long opening hours and busy days.
- The time axis includes padding so the first time label is visible. Tick spacing adapts to the displayed time range.
- Appointment cards use the existing cream/sage palette, clearer typography and status indicators. Dense overlapping appointments collapse into selectable groups; every booking remains accessible.
- Status dropdown choices have colored indicators.
- Booking details keep the header and action buttons fixed while the middle section scrolls. Notes wrap, including unbroken strings; long notes scroll on hover or keyboard focus.
- Customer details include a New appointment action with that customer preselected.

## Management pages

- Services, resources, locations and staff default to searchable lists, with active/inactive filters, eight entries per page and an optional card view.
- Native checkboxes have consistent styling and keyboard focus indicators.
- Staff contact forms and weekly rota use compact layouts.
- Recurring availability uses seven day buttons: activate a day, set its times, and save the week. Weekday presets, copying Monday and split hours reduce repetitive entry.

## Backend contract

PUT /api/v1/{staff|locations|resources}/{ownerId}/availability/rules replaces the owner's recurring rules atomically. Body: {"rules":[{"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"17:00","active":true}]}.

The endpoint validates the entire draft before replacing rules, rejects active overlaps and reversed intervals, supports inactive rules and clearing a schedule, enforces tenant ownership and ADMIN/STAFF authorization, and preserves exceptions and dated staff rotas. No database migration is needed. Restart the backend to load the new endpoint.

## Verification

- Backend clean verify: 88 tests passed; packaged application successfully.
- Frontend: 51 API, identity, timezone and rendering checks passed.
- TypeScript, lint and production build passed.
- Automated browser verification was unavailable in this environment. Check the layouts at your usual screen size in the running application, especially dense calendars and the narrow-screen staff rota.

## Git Bash

```bash
cd /f/multi-booking-saas
git status --short
git diff --check
git add README.md docs/visual-refinements.md
git add backend/src/main/java/com/example/booking/tenantdata/availability/
git add backend/src/test/java/com/example/booking/tenantdata/BackendAssignmentIntegrationTest.java
git add frontend/src/components/ frontend/src/features/ frontend/src/main.tsx frontend/tests/assignmentFlows.mjs
git diff --cached --stat
git diff --cached
# After reviewing the staged changes:
git commit -m "Refine calendar and management workflows"
git push
```

Review the staged diff because these directory commands include any other edits present there. No commit or push was performed automatically. The accompanying zip contains only the files changed for this task, with repository-relative paths; it excludes dependencies, build products and database data.
