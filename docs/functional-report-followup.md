# Functional report follow-up

Applied directly to F:/multi-booking-saas. No commit or push performed.

## Booking creation and rescheduling

Both forms default to today in the tenant timezone. A persistent availability section explains missing prerequisites, shows loading, lists returned combinations, explains empty results and presents errors with Retry. Requests stop after 20 seconds instead of remaining in network loading indefinitely. A service configured with staff Not used intentionally has no staff selector and now explains this. Saving still requires an exact returned combination; validation has not been relaxed.

Added render/API checks cover a reschedule form with returned slots, an empty response and an error, as well as the create prerequisite state. The original Chrome failure could not be reproduced here because the browser runtime still fails to initialize. The changes remove silent states, but do not establish a confirmed root cause for the reported interaction failure. Retest the reported create/reschedule steps in Chrome; if availability is empty, inspect service eligibility, native owner hours, closures, minimum notice and existing appointments.

## Entity identity

Identical names are allowed because different staff can legitimately share a name and different rooms can share a label. Duplicate labels now include available email/phone/address details plus a stable ID suffix. This applies to staff/location directories, service eligibility, booking assignment filters, returned combination labels, owner schedule selectors and dashboard team labels. Existing data is preserved. No duplicate records were deleted and no uniqueness constraint was introduced.

## Navigation and intended behavior

- Dashboard New appointment navigates to /appointments?openCreate=1 and opens the editor immediately. Closing consumes the flag while preserving other query parameters; View calendar remains a normal navigation action.
- Staff scheduling remains next-week first, matching the original requested planning workflow, with an explicit Next-week planning label.
- Calendar day counts now use 1 appointment.
- Empty sign-in remains Keycloak's hosted behavior. There is no project-owned login theme to patch; generic credential errors are retained rather than introducing a custom theme solely for this low-severity item.

## Verification

58 frontend API, identity, timezone and server-rendering checks passed. TypeScript, lint, production build and git diff --check passed. No backend source changed in this follow-up; the preceding backend verify passed 88 tests. Browser interaction verification remains outstanding. Frontend changes require no migration or backend restart.

## Git Bash

```bash
cd /f/multi-booking-saas
git status --short
git add README.md docs/functional-report-followup.md
git add frontend/src/features/appointments/AppointmentsPage.tsx frontend/src/features/appointments/BookingEditor.tsx frontend/src/features/appointments/appointmentApi.ts
git add frontend/src/features/assignments/AssignmentFilters.tsx frontend/src/features/assignments/assignmentTypes.ts frontend/src/features/assignments/assignmentLabels.ts frontend/src/features/assignments/entityLabels.ts
git add frontend/src/features/availability/OwnerSchedule.tsx frontend/src/features/dashboard/DashboardPage.tsx
git add frontend/src/features/locations/LocationsPage.tsx frontend/src/features/locations/LocationStaffPanel.tsx
git add frontend/src/features/services/ServiceForm.tsx frontend/src/features/staff/StaffPage.tsx frontend/src/features/staff/StaffWeekEditor.tsx
git add frontend/src/components/ManagementRefinement.css frontend/tests/assignmentFlows.mjs
git diff --cached
git commit -m "Clarify booking availability and distinguish duplicate assignments"
git push
```

Review the staged diff because some listed files may contain earlier uncommitted work. The zip contains current versions of the files changed for this follow-up and excludes dependencies and build output.
