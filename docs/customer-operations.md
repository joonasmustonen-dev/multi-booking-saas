# Operational customer features

Customers now have a searchable contact directory, paginated booking history, factual attendance counts, completed-visit service summaries, and an optional preferred staff member. Forms and detail views use the existing warm colors, rounded cards, and searchable controls.

## Use

- Search Customers by name, email or phone. The directory shows at most 50 matches; refine the query for larger directories.
- Select a customer to see contact links, completed/cancelled/no-show counts, last completed visit, most-booked services and booking history. History includes all statuses, newest first, with 20 entries per page.
- Save an optional preferred staff member in the customer form. New preferences must reference active staff in the same workspace. Existing inactive/archived preferences can be retained or cleared.
- Appointment creation searches at most 25 matching customers rather than downloading the directory. Customer context shows last visit, preferred staff, attendance and usual services.
- Calendar appointment details include this context and a customer detail link alongside the existing contact details.
- Preferred staff is informational and does not automatically change assignments or bypass service eligibility, location restrictions or availability.

Phone search removes punctuation and ignores leading zeros, so `040 123` can match stored `+358 (40) 123-9876`. It is partial matching, not identity verification or deduplication. Names/emails are case-insensitive; SQL wildcard characters are escaped.

## API

```text
GET /api/v1/customers/search?q=<name/email/phone>&limit=25
GET /api/v1/customers/{id}/activity?page=0&size=20
```

Both APIs require TENANT_ADMIN or STAFF and use the authenticated tenant database. Search allows limits 1–50 and queries up to 100 characters. Empty search browses a limited set ordered by last name, first name and ID. The legacy customer list endpoint remains available, but the new directory and picker use bounded search.

Activity returns customer, preferredStaffName, totalBookings, completedBookings, cancelledBookings, noShows, lastVisit, mostBookedServices, bookings and pagination fields. History size is 1–50. Invalid parameters return 400; missing customers return 404; customer-role access returns 403.

Last visit and the top five services use COMPLETED appointments with past end times. Attendance counts reflect saved statuses; they do not infer attendance. History omits appointment notes.

Customer POST/PUT accepts preferredStaffId (UUID or null). PUT is a full update: null or omission clears the preference. The frontend sends the current value when retaining it. Invalid new staff assignments return 400.

Deletion is retained for customers without booking history. Referenced records return 409; the UI disables deletion for those records. This operation is not an erasure/anonymization workflow.

## Database and verification

V13 adds nullable preferred_staff_id with a staff foreign key and an appointment-history index. Backend startup migrates ACTIVE tenants; existing customers initially have no preference. Local integration checks already applied V13 to the test tenants.

Validation passed 86 backend tests, 42 frontend checks, JAR packaging, TypeScript, frontend build and lint. Focused tests cover formatted phone search, escaped wildcards, bounded queries, preference persistence/clearing, inactive staff rejection, factual history, pagination, tenant separation, role access and deletion safeguards.

Browser verification was blocked by tool initialization failure. Retention automation, customer export/anonymization and processor agreements remain separate production work. No new customer health fields or unrestricted customer notes are introduced.
