import assert from "node:assert/strict";
import { registerHooks } from "node:module";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import ts from "typescript";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import {
    IsRestoringProvider,
    QueryClient,
    QueryClientProvider
} from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

globalThis.isSecureContext = true;
const hooks = registerHooks({
    resolve(specifier, context, next) {
        if (specifier.startsWith(".") && context.parentURL?.includes("/src/")) {
            const candidate = new URL(specifier, context.parentURL);
            for (const extension of ["", ".ts", ".tsx"]) {
                const url = candidate.href + extension;
                if (existsSync(fileURLToPath(url)))
                    return { url, shortCircuit: true };
            }
        }
        return next(specifier, context);
    },
    load(url, context, next) {
        if (url.endsWith(".css"))
            return {
                format: "module",
                source: "export default {};",
                shortCircuit: true
            };
        if (/\.tsx?$/.test(url))
            return {
                format: "module",
                source: ts.transpileModule(
                    readFileSync(fileURLToPath(url), "utf8"),
                    {
                        compilerOptions: {
                            target: ts.ScriptTarget.ES2022,
                            module: ts.ModuleKind.ESNext,
                            jsx: ts.JsxEmit.ReactJSX
                        }
                    }
                ).outputText,
                shortCircuit: true
            };
        return next(url, context);
    }
});
const server = {
    ssrLoadModule: path => import(new URL(".." + path, import.meta.url)),
    close: async () => hooks.deregister()
};
const results = [];
const check = (name, action) => {
    action();
    results.push({ name, passed: true });
};
try {
    const labels = await server.ssrLoadModule(
        "/src/features/assignments/assignmentLabels.ts"
    );
    const dates = await server.ssrLoadModule(
        "/src/features/availability/scheduleDates.ts"
    );
    const base = {
        staffId: "staff-a",
        locationId: "room-a",
        resourceId: null,
        start: "2026-09-21T10:00:00+03:00",
        end: "2026-09-21T11:00:00+03:00"
    };
    check(
        "Same-time slots retain distinct staff/location/resource identities",
        () => {
            const slots = [
                base,
                { ...base, staffId: "staff-b" },
                { ...base, locationId: "room-b" },
                { ...base, resourceId: "camera" }
            ];
            assert.equal(new Set(slots.map(labels.slotKey)).size, 4);
            assert.equal(
                slots.find(
                    slot => labels.slotKey(slot) === labels.slotKey(slots[2])
                ).locationId,
                "room-b"
            );
        }
    );
    check("Resource-free labels show native staff/location names", () =>
        assert.equal(
            labels.appointmentAssignmentLabel({
                staffName: "Sofia",
                locationName: "Room 1",
                resourceName: null
            }),
            "Sofia · Room 1"
        )
    );
    check(
        "Exception editing uses tenant time rather than browser/UTC time",
        () =>
            assert.equal(
                dates.localDateTimeInZone(
                    "2026-09-21T10:00:00+03:00",
                    "Europe/Helsinki"
                ),
                "2026-09-21T10:00"
            )
    );

    const auth = (await server.ssrLoadModule("/src/auth/keycloak.ts")).default;
    auth.token = "test-token";
    auth.updateToken = async () => false;
    const requests = [];
    globalThis.fetch = async (url, options = {}) => {
        requests.push({
            url: String(url),
            method: options.method ?? "GET",
            body: options.body ? JSON.parse(options.body) : null
        });
        return new Response(JSON.stringify([]), {
            status: 200,
            headers: { "Content-Type": "application/json" }
        });
    };
    const api = await server.ssrLoadModule(
        "/src/features/appointments/appointmentApi.ts"
    );
    await api.getAvailability("haircut", "2026-09-21", {
        staffId: "staff-a",
        locationId: "room-a",
        resourceId: ""
    });
    check(
        "Availability requests send staff and location filters and omit empty resource",
        () => {
            const url = new URL(requests.at(-1).url);
            assert.equal(url.searchParams.get("staffId"), "staff-a");
            assert.equal(url.searchParams.get("locationId"), "room-a");
            assert.equal(url.searchParams.has("resourceId"), false);
        }
    );
    await api.createAppointment({
        customerId: "emma",
        serviceId: "haircut",
        staffId: base.staffId,
        locationId: base.locationId,
        resourceId: null,
        startAt: base.start,
        notes: null
    });
    check(
        "Appointment create sends complete selected assignments including null resource",
        () => {
            assert.equal(requests.at(-1).body.locationId, "room-a");
            assert.equal(requests.at(-1).body.resourceId, null);
        }
    );
    await api.rescheduleAppointment("booking", {
        ...base,
        startAt: base.start
    });
    check("Reschedule request includes native assignments", () => {
        assert.equal(requests.at(-1).body.staffId, "staff-a");
        assert.equal(requests.at(-1).body.locationId, "room-a");
    });
    await api.getRescheduleAvailability("booking", "2026-09-21", {
        staffId: "staff-a"
    });
    check("Reschedule picker uses the scoped self-excluding endpoint", () =>
        assert.equal(
            new URL(requests.at(-1).url).pathname,
            "/api/v1/appointments/booking/availability"
        )
    );
    await api.getAppointments("2026-09-21", "2026-09-27", {
        staffId: "staff-a",
        locationId: "room-a"
    });
    check("Calendar API transmits native staff/location filters", () => {
        const url = new URL(requests.at(-1).url);
        assert.equal(url.searchParams.get("staffId"), "staff-a");
        assert.equal(url.searchParams.get("locationId"), "room-a");
    });
    const schedules = await server.ssrLoadModule(
        "/src/features/availability/availabilityApi.ts"
    );
    await schedules.saveAvailabilityRule(
        "staff",
        "staff-a",
        {
            dayOfWeek: "MONDAY",
            startTime: "09:00",
            endTime: "17:00",
            active: false
        },
        "rule"
    );
    check(
        "Staff recurring rule edits use native PUT route and activation",
        () => {
            assert.equal(
                new URL(requests.at(-1).url).pathname,
                "/api/v1/staff/staff-a/availability/rules/rule"
            );
            assert.equal(requests.at(-1).method, "PUT");
            assert.equal(requests.at(-1).body.active, false);
        }
    );
    await schedules.saveAvailabilityException("locations", "room-a", {
        startAt: "2026-09-21T10:00",
        endAt: "2026-09-21T11:00",
        available: false
    });
    check("Location exceptions preserve tenant-local request times", () => {
        assert.equal(
            new URL(requests.at(-1).url).pathname,
            "/api/v1/locations/room-a/availability/exceptions"
        );
        assert.equal(requests.at(-1).body.startAt, "2026-09-21T10:00");
    });

    const named = (id, name) => ({
        id,
        name,
        active: true,
        createdAt: base.start
    });
    const catalogs = {
        staff: [
            {
                ...named("staff-a", "Sofia"),
                email: "sofia@example.com",
                phone: "+358 123",
                freeAgent: true,
                locationIds: []
            }
        ],
        locations: [named("room-a", "Room 1")],
        resources: [
            {
                ...named("camera", "Camera A"),
                type: "EQUIPMENT",
                description: "Mirrorless kit"
            }
        ]
    };
    const offering = {
        ...named("haircut", "Haircut"),
        description: "A fresh cut.",
        durationMinutes: 60,
        price: 50,
        currency: "EUR",
        staffIds: ["staff-a"],
        locationIds: ["room-a"],
        resourceIds: [],
        staffRequirement: "REQUIRED",
        locationRequirement: "REQUIRED",
        resourceRequirement: "FORBIDDEN"
    };
    const client = new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: Infinity } }
    });
    for (const [key, value] of [
        ["staff", catalogs.staff],
        ["locations", catalogs.locations],
        ["resources", catalogs.resources],
        ["services", [offering]],
        ["customers", [{ id: "emma", firstName: "Emma", lastName: "Wilson" }]],
        [
            "tenant-settings",
            {
                timeZone: "Europe/Helsinki",
                businessName: "My Studio",
                contactEmail: "hello@example.com",
                contactPhone: "123",
                defaultCurrency: "USD",
                slotIntervalMinutes: 30,
                minimumNoticeMinutes: 60,
                bookingHorizonDays: 90,
                calendarStartHour: 8,
                calendarEndHour: 18,
                weekStartsOn: 0,
                defaultAppointmentStatus: "CONFIRMED"
            }
        ]
    ])
        client.setQueryData([key], value);
    const wrap = element =>
        renderToStaticMarkup(
            React.createElement(
                QueryClientProvider,
                { client },
                React.createElement(MemoryRouter, null, element)
            )
        );
    const serviceForm = (
        await server.ssrLoadModule("/src/features/services/ServiceForm.tsx")
    ).default;
    const formHtml = wrap(
        React.createElement(serviceForm, {
            service: offering,
            catalogs,
            onSubmit: async () => {},
            onCancel() {}
        })
    );
    check(
        "Service editor renders independent native requirement and eligibility controls",
        () => {
            assert(formHtml.includes("Sofia"));
            assert(formHtml.includes("Room 1"));
            assert.equal(
                (formHtml.match(/Booking requirement/g) ?? []).length,
                3
            );
        }
    );
    const servicePage = (
        await server.ssrLoadModule("/src/features/services/ServicesPage.tsx")
    ).default;
    const servicesHtml = wrap(React.createElement(servicePage));
    check("Services cards render named assignments and requirements", () => {
        assert(servicesHtml.includes("Sofia"));
        assert(servicesHtml.includes("Room 1"));
        assert(servicesHtml.includes("Not used"));
    });
    const filters = (
        await server.ssrLoadModule(
            "/src/features/assignments/AssignmentFilters.tsx"
        )
    ).default;
    const filterHtml = wrap(
        React.createElement(filters, {
            service: offering,
            catalogs,
            values: {
                staffId: "staff-a",
                locationId: "room-a",
                resourceId: ""
            },
            onChange() {}
        })
    );
    check("Booking selectors hide forbidden resource category", () => {
        assert(filterHtml.includes("Sofia"));
        assert(!filterHtml.includes("Camera A"));
    });
    const date = new Intl.DateTimeFormat("en-CA", {
        timeZone: "Europe/Helsinki"
    }).format(new Date());
    const item = {
        id: "booking",
        customerId: "emma",
        customerName: "Emma Wilson",
        serviceId: "haircut",
        serviceName: "Haircut",
        staffId: "staff-a",
        staffName: "Sofia",
        locationId: "room-a",
        locationName: "Room 1",
        resourceId: null,
        resourceName: null,
        startAt: `${date}T10:00:00+03:00`,
        endAt: `${date}T11:00:00+03:00`,
        status: "CONFIRMED"
    };
    const editor = (
        await server.ssrLoadModule(
            "/src/features/appointments/BookingEditor.tsx"
        )
    ).default;
    const editorHtml = wrap(
        React.createElement(editor, {
            appointment: item,
            onSubmit: async () => {},
            onCancel() {}
        })
    );
    check(
        "Reschedule form renders native current assignments and tenant timezone",
        () => {
            assert(editorHtml.includes("Sofia"));
            assert(editorHtml.includes("Room 1"));
            assert(editorHtml.includes("Europe/Helsinki"));
        }
    );
    const dashboardSummary = {
        timeZone: "Europe/Helsinki",
        today: date,
        todaysBookings: 1,
        openSlots: 5,
        customers: 1,
        bookingsThisWeek: 1,
        dailyBookings: [{ date, count: 1 }],
        todaysAppointments: [item],
        team: [{ staffId: "staff-a", name: "Sofia", todaysBookings: 1 }],
        popularServices: [
            { serviceId: "haircut", name: "Haircut", bookings: 1 }
        ],
        statusCounts: [{ status: "CONFIRMED", count: 1 }]
    };
    client.setQueryData(["dashboard-summary"], dashboardSummary);
    const dashboardPage = (
        await server.ssrLoadModule("/src/features/dashboard/DashboardPage.tsx")
    ).default;
    const dashboardHtml = wrap(React.createElement(dashboardPage));
    check(
        "Dashboard renders native staff team and resource-free appointment assignments",
        () => {
            assert(dashboardHtml.includes("Sofia"));
            assert(dashboardHtml.includes("Room 1"));
            assert(!dashboardHtml.includes("staff resources"));
        }
    );
    const calendarPage = (
        await server.ssrLoadModule(
            "/src/features/appointments/AppointmentsPage.tsx"
        )
    ).default;
    wrap(React.createElement(calendarPage));
    for (const query of client.getQueryCache().getAll())
        if (query.queryKey[0] === "appointments")
            client.setQueryData(query.queryKey, [item]);
    const calendarHtml = wrap(React.createElement(calendarPage));
    check(
        "Calendar renders native filters and named resource-free booking cards",
        () => {
            assert(calendarHtml.includes("All staff"));
            assert(calendarHtml.includes("All locations"));
            assert(calendarHtml.includes("Emma Wilson"));
            assert(calendarHtml.includes("Room 1"));
        }
    );
    check(
        "Calendar offers exactly staff/location/service/status filters",
        () => {
            assert.equal(
                (calendarHtml.match(/role="combobox"/g) ?? []).length,
                4
            );
            assert(!calendarHtml.includes("All customers"));
            assert(!calendarHtml.includes("All resources"));
        }
    );
    check("Service editor uses muted colored assignment surfaces", () =>
        assert(formHtml.includes("tinted-panel service-editor"))
    );
    const settingsApi = await server.ssrLoadModule(
        "/src/features/settings/settingsApi.ts"
    );
    const workspace = client.getQueryData(["tenant-settings"]);
    await settingsApi.updateTenantSettings(workspace);
    check("Settings API persists all displayed workspace policies", () => {
        const request = requests.at(-1);
        assert.equal(request.method, "PUT");
        assert.equal(request.body.businessName, "My Studio");
        assert.equal(request.body.slotIntervalMinutes, 30);
        assert.equal(request.body.minimumNoticeMinutes, 60);
        assert.equal(request.body.defaultAppointmentStatus, "CONFIRMED");
        assert.equal(request.body.weekStartsOn, 0);
    });
    const locationApi = await server.ssrLoadModule(
        "/src/features/locations/locationApi.ts"
    );
    const location = {
        ...named("room-a", "Room 1"),
        description: "Ground floor",
        addressLine: "Main Street 10",
        city: "Helsinki",
        postalCode: "00100",
        countryCode: "FI",
        phone: "123"
    };
    const { id, createdAt, ...details } = location;
    void createdAt;
    await locationApi.saveLocation(details, id);
    check(
        "Location update sends physical location details to the native endpoint",
        () => {
            const request = requests.at(-1);
            assert.equal(
                new URL(request.url).pathname,
                "/api/v1/locations/room-a"
            );
            assert.equal(request.body.addressLine, "Main Street 10");
            assert.equal(request.body.countryCode, "FI");
            assert.equal(request.body.active, true);
        }
    );
    client.setQueryData(["locations"], [location]);
    const locationsPage = (
        await server.ssrLoadModule("/src/features/locations/LocationsPage.tsx")
    ).default;
    const locationsHtml = wrap(React.createElement(locationsPage));
    check(
        "Locations page renders native details and schedule navigation",
        () => {
            assert(locationsHtml.includes("Main Street 10"));
            assert(locationsHtml.includes("Helsinki"));
            assert(locationsHtml.includes("Add location"));
            assert(locationsHtml.includes('id="locations-schedule"'));
        }
    );
    const resourcePage = (
        await server.ssrLoadModule("/src/features/resources/ResourcesPage.tsx")
    ).default;
    const resourceHtml = wrap(React.createElement(resourcePage));
    check(
        "Resources page uses the matching management cards and native actions",
        () => {
            assert(resourceHtml.includes("service-card resource-card"));
            assert(resourceHtml.includes("Camera A"));
            assert(resourceHtml.includes("Deactivate"));
            assert(resourceHtml.includes("Add resource"));
        }
    );
    const settingsPage = (
        await server.ssrLoadModule("/src/features/settings/SettingsPage.tsx")
    ).default;
    const settingsHtml = wrap(React.createElement(settingsPage));
    check(
        "Settings page renders real saved business/booking/calendar controls",
        () => {
            assert(settingsHtml.includes("My Studio"));
            assert(settingsHtml.includes("Slot spacing (minutes)"));
            assert(settingsHtml.includes("Minimum booking notice"));
            assert(settingsHtml.includes("Week starts on"));
            assert(settingsHtml.includes("Only a tenant administrator"));
        }
    );
    const datesApi = await server.ssrLoadModule(
        "/src/features/appointments/dateUtils.ts"
    );
    check("Calendar respects saved Sunday or Monday week starts", () => {
        assert.equal(datesApi.startOfWeek("2026-09-16", 0), "2026-09-13");
        assert.equal(datesApi.startOfWeek("2026-09-16", 1), "2026-09-14");
    });
    const shell = (await server.ssrLoadModule("/src/components/AppLayout.tsx"))
        .default;
    const shellHtml = wrap(React.createElement(shell));
    check("Sidebar includes Locations and persisted business name", () => {
        assert(shellHtml.includes('href="/locations"'));
        assert(shellHtml.includes("My Studio"));
    });
    check("Sidebar has Staff and no standalone Availability link", () => {
        assert(shellHtml.includes('href="/staff"'));
        assert(!shellHtml.includes('href="/availability"'));
    });
    const staffApi = await server.ssrLoadModule(
        "/src/features/staff/staffApi.ts"
    );
    const member = catalogs.staff[0];
    await staffApi.saveStaff(
        {
            name: member.name,
            email: member.email,
            phone: member.phone,
            active: true,
            freeAgent: false,
            locationIds: ["room-a"]
        },
        member.id
    );
    check("Staff editor API sends contact details and location policy", () => {
        const request = requests.at(-1);
        assert.equal(request.method, "PUT");
        assert.equal(request.body.email, "sofia@example.com");
        assert.equal(request.body.phone, "+358 123");
        assert.equal(request.body.freeAgent, false);
        assert.deepEqual(request.body.locationIds, ["room-a"]);
    });
    await staffApi.removeStaff(member.id);
    check("Staff removal uses the native archive endpoint", () =>
        assert.equal(requests.at(-1).method, "DELETE")
    );
    const rota = await server.ssrLoadModule("/src/features/staff/rotaDates.ts");
    const nextWeek = rota.nextStaffWeek(date);
    const shifts = [{ date: nextWeek, startTime: "09:00", endTime: "17:00" }];
    await staffApi.saveStaffWeek(member.id, nextWeek, shifts);
    check("Weekly schedule saves all dated shifts in one request", () => {
        const request = requests.at(-1);
        assert.equal(
            new URL(request.url).pathname,
            "/api/v1/staff/staff-a/schedule"
        );
        assert.equal(
            new URL(request.url).searchParams.get("weekStart"),
            nextWeek
        );
        assert.deepEqual(request.body.shifts, shifts);
    });
    check(
        "Copying a rota preserves weekday positions across year boundaries",
        () => {
            assert.deepEqual(
                rota.copyShiftsToWeek(
                    [
                        {
                            date: "2026-12-31",
                            startTime: "09:00",
                            endTime: "17:00"
                        }
                    ],
                    "2026-12-28",
                    "2027-01-04"
                ),
                [{ date: "2027-01-07", startTime: "09:00", endTime: "17:00" }]
            );
            assert.equal(rota.nextStaffWeek("2026-09-20"), "2026-09-21");
        }
    );
    client.setQueryData(["staff-week", member.id, nextWeek], {
        weekStart: nextWeek,
        overridden: true,
        shifts
    });
    client.setQueryData(["availability-rules", "staff", member.id], []);
    client.setQueryData(["availability-exceptions", "staff", member.id], []);
    const staffPage = (
        await server.ssrLoadModule("/src/features/staff/StaffPage.tsx")
    ).default;
    const staffHtml = wrap(React.createElement(staffPage));
    check(
        "Staff page renders contacts, free agents, and next-week rota controls",
        () => {
            assert(staffHtml.includes("sofia@example.com"));
            assert(staffHtml.includes("+358 123"));
            assert(staffHtml.includes("Free agent"));
            assert(staffHtml.includes("Monday"));
            assert(staffHtml.includes("Sunday"));
            assert(staffHtml.includes("Copy previous week"));
            assert(staffHtml.includes("Save this week"));
            assert(staffHtml.includes("Recurring hours and time off"));
        }
    );
    check(
        "Resources show descriptions and embedded native availability",
        () => {
            assert(resourceHtml.includes("Mirrorless kit"));
            assert(resourceHtml.includes('id="resources-schedule"'));
        }
    );
    const detailPanel = (
        await server.ssrLoadModule(
            "/src/features/appointments/AppointmentDetails.tsx"
        )
    ).default;
    client.setQueryData(["customer", item.customerId], {
        id: item.customerId,
        firstName: "Emma",
        lastName: "Wilson",
        email: "emma@example.com",
        phone: "+358 456"
    });
    client.setQueryData(["appointment-detail", item.id], {
        ...item,
        notes: "Bring reference photo",
        createdAt: item.startAt
    });
    const detailHtml = wrap(
        React.createElement(detailPanel, {
            appointment: item,
            timeZone: "Europe/Helsinki",
            onClose() {},
            onReschedule() {},
            onCancel: async () => {},
            onStatus: async () => {}
        })
    );
    check(
        "Appointment detail dialog renders styled assignments, contacts, notes, and actions",
        () => {
            assert(detailHtml.includes('role="dialog"'));
            assert(detailHtml.includes("appointment-detail-hero"));
            assert(detailHtml.includes("Room 1"));
            assert(detailHtml.includes("emma@example.com"));
            assert(detailHtml.includes("Bring reference photo"));
            assert(detailHtml.includes("Mark completed"));
            assert(detailHtml.includes("Reschedule"));
        }
    );
    check(
        "Calendar cards carry stylized hover content instead of browser titles",
        () => {
            assert(calendarHtml.includes('data-tooltip="Emma Wilson'));
            assert(!calendarHtml.includes('title="Emma Wilson'));
        }
    );
    const selectHelpers = await server.ssrLoadModule(
        "/src/components/selectOptions.ts"
    );
    const choices = [
        { value: "a", label: "Sofía", description: "sofia@example.com" },
        { value: "b", label: "Alex" },
        { value: "c", label: "Inactive", disabled: true }
    ];
    check(
        "Typed dropdown search matches names, accents, and contact metadata",
        () => {
            assert.equal(
                selectHelpers.filterSelectOptions(choices, " SOFIA ")[0].value,
                "a"
            );
            assert.equal(
                selectHelpers.filterSelectOptions(choices, "example.com")[0]
                    .value,
                "a"
            );
            assert.equal(
                selectHelpers.filterSelectOptions(choices, "ALEx")[0].value,
                "b"
            );
        }
    );
    check(
        "Dropdown keyboard navigation wraps and skips disabled entries",
        () => {
            assert.equal(selectHelpers.nextEnabledOption(choices, 1, 1), 0);
            assert.equal(selectHelpers.nextEnabledOption(choices, 0, -1), 1);
        }
    );
    const search = (
        await server.ssrLoadModule("/src/components/SearchSelect.tsx")
    ).default;
    const fixedHtml = wrap(
        React.createElement(search, {
            value: "b",
            options: choices,
            onChange() {},
            ariaLabel: "Status",
            searchable: false
        })
    );
    const searchableHtml = wrap(
        React.createElement(search, {
            value: "a",
            options: choices,
            onChange() {},
            ariaLabel: "Staff"
        })
    );
    check(
        "Entity dropdowns accept typing while fixed choices use a button",
        () => {
            assert(searchableHtml.includes("<input"));
            assert(searchableHtml.includes('aria-autocomplete="list"'));
            assert(!fixedHtml.includes("<input"));
            assert(fixedHtml.includes("<button"));
        }
    );
    const customerApi = await server.ssrLoadModule(
        "/src/features/customers/customerApi.ts"
    );
    await customerApi.searchCustomers("+358 40", 25);
    check(
        "Customer search sends bounded encoded phone queries to the native endpoint",
        () => {
            const url = new URL(requests.at(-1).url);
            assert.equal(url.pathname, "/api/v1/customers/search");
            assert.equal(url.searchParams.get("q"), "+358 40");
            assert.equal(url.searchParams.get("limit"), "25");
        }
    );
    await customerApi.updateCustomer("emma", {
        firstName: "Emma",
        lastName: "Wilson",
        email: null,
        phone: "+358 456",
        preferredStaffId: "staff-a"
    });
    check(
        "Customer edits persist preferred staff independently of appointment assignments",
        () => assert.equal(requests.at(-1).body.preferredStaffId, "staff-a")
    );
    const activity = {
        customer: {
            id: "emma",
            firstName: "Emma",
            lastName: "Wilson",
            email: "emma@example.com",
            phone: "+358 456",
            preferredStaffId: "staff-a",
            createdAt: base.start
        },
        preferredStaffName: "Sofia",
        totalBookings: 4,
        completedBookings: 2,
        cancelledBookings: 1,
        noShows: 1,
        lastVisit: base.start,
        mostBookedServices: [
            { serviceId: "haircut", name: "Haircut", visits: 2 }
        ],
        bookings: [{ ...item, status: "NO_SHOW" }],
        page: 0,
        size: 20,
        totalElements: 4,
        totalPages: 1
    };
    client.setQueryData(["customer-activity", "emma", 0, 20], activity);
    client.setQueryData(["customer-activity", "emma", 0, 1], activity);
    client.setQueryData(["customer-search", "", 50], [activity.customer]);
    client.setQueryData(["customer-search", ""], [activity.customer]);
    const customerDetail = (
        await server.ssrLoadModule("/src/features/customers/CustomerDetail.tsx")
    ).default;
    const customerDetailHtml = wrap(
        React.createElement(customerDetail, {
            id: "emma",
            timeZone: "Europe/Helsinki",
            onEdit() {},
            onClose() {}
        })
    );
    check(
        "Customer details show factual attendance, history, and preferred staff",
        () => {
            assert(customerDetailHtml.includes("Booking history"));
            assert(customerDetailHtml.includes("No-shows"));
            assert(customerDetailHtml.includes("Sofia"));
            assert(customerDetailHtml.includes("NO SHOW"));
            assert(customerDetailHtml.includes("2 visits"));
        }
    );
    const context = (
        await server.ssrLoadModule(
            "/src/features/customers/CustomerContext.tsx"
        )
    ).default;
    const contextHtml = wrap(
        React.createElement(context, {
            customerId: "emma",
            timeZone: "Europe/Helsinki"
        })
    );
    check(
        "Booking context surfaces preferences and attendance with a customer link",
        () => {
            assert(contextHtml.includes("2 completed"));
            assert(contextHtml.includes("1 no-shows"));
            assert(contextHtml.includes("Sofia"));
            assert(contextHtml.includes("/customers?customerId=emma"));
        }
    );
    const directory = (
        await server.ssrLoadModule("/src/features/customers/CustomersPage.tsx")
    ).default;
    const directoryHtml = wrap(React.createElement(directory));
    check(
        "Customer directory renders searchable contact cards in the shared design",
        () => {
            assert(directoryHtml.includes('aria-label="Search customers"'));
            assert(directoryHtml.includes("customer-card"));
            assert(directoryHtml.includes("emma@example.com"));
            assert(directoryHtml.includes("+358 456"));
        }
    );
    const presentation = await server.ssrLoadModule(
        "/src/features/appointments/calendarPresentation.ts"
    );
    const calendarMath = await server.ssrLoadModule(
        "/src/features/appointments/calendarLayout.ts"
    );
    const dense = [
        {
            ...item,
            id: "short-a",
            startAt: `${date}T10:00:00+03:00`,
            endAt: `${date}T10:10:00+03:00`
        },
        {
            ...item,
            id: "short-b",
            startAt: `${date}T10:10:00+03:00`,
            endAt: `${date}T10:20:00+03:00`
        },
        {
            ...item,
            id: "late",
            startAt: `${date}T23:50:00+03:00`,
            endAt: `${date}T23:59:00+03:00`
        }
    ];
    const display = presentation.calendarDisplayGroups(
        calendarMath.layoutDayAppointments(dense, "Europe/Helsinki"),
        0,
        1440,
        320
    );
    check(
        "Full-day calendar groups unreadable cards without losing bookings or overflowing",
        () => {
            assert.deepEqual(
                display.flatMap(g => g.items.map(i => i.appointment.id)).sort(),
                dense.map(i => i.id).sort()
            );
            assert(display.some(g => g.grouped && g.items.length === 2));
            assert(display.every(g => g.top >= 0 && g.top + g.height <= 320));
        }
    );
    check(
        "Calendar time ticks retain both boundaries and reduce density on long ranges",
        () => {
            const ticks = presentation.calendarTickMinutes(0, 1440, 320);
            assert.equal(ticks[0], 0);
            assert.equal(ticks.at(-1), 1440);
            assert(ticks.length < 15);
            const usual = presentation.calendarTickMinutes(480, 1080, 640);
            assert.equal(usual[0], 480);
            assert.equal(usual.length, 11);
        }
    );
    const browserModule = await server.ssrLoadModule(
        "/src/components/CatalogBrowser.tsx"
    );
    const catalogFilter = await server.ssrLoadModule(
        "/src/components/catalogFilter.ts"
    );
    const catalogItems = Array.from({ length: 24 }, (_, index) => ({
        id: `entry-${index}`,
        name: index === 0 ? "Sofía" : `Entry ${index}`,
        active: index % 2 === 0
    }));
    check(
        "Catalog search combines accent-insensitive lookup and activity filtering",
        () => {
            assert.equal(
                catalogFilter.filterCatalog(catalogItems, "sofia", "active")
                    .length,
                1
            );
            assert.equal(
                catalogFilter.filterCatalog(catalogItems, "", "inactive")
                    .length,
                12
            );
        }
    );
    const browseHtml = wrap(
        React.createElement(
            browserModule.default,
            { items: catalogItems, label: "Staff" },
            visible =>
                React.createElement(
                    "div",
                    {},
                    visible.map(i =>
                        React.createElement("article", { key: i.id }, i.name)
                    )
                )
        )
    );
    check(
        "Management catalogs default to a bounded list with page controls",
        () => {
            assert(browseHtml.includes("catalog-list"));
            assert(browseHtml.includes("1–8 of 24"));
            assert.equal((browseHtml.match(/<article/g) ?? []).length, 8);
            assert(browseHtml.includes("Page 1 of 3"));
        }
    );
    const drafts = await server.ssrLoadModule(
        "/src/features/availability/recurringDraft.ts"
    );
    check(
        "Recurring day toggles preserve times and weekday presets leave weekends closed",
        () => {
            const mon = [
                {
                    dayOfWeek: "MONDAY",
                    startTime: "10:00",
                    endTime: "16:00",
                    active: true
                }
            ];
            const closed = drafts.toggleRecurringDay(mon, "MONDAY", false);
            assert.equal(closed[0].startTime, "10:00");
            assert.equal(
                drafts.toggleRecurringDay(closed, "MONDAY", true)[0].active,
                true
            );
            assert.equal(drafts.recurringWeekdaysPreset().length, 5);
            assert.equal(
                drafts
                    .validateRecurringDraft([
                        ...mon,
                        { ...mon[0], startTime: "15:00", endTime: "17:00" }
                    ])
                    .includes("overlap"),
                true
            );
        }
    );
    await schedules.replaceAvailabilityRules(
        "locations",
        "room-a",
        drafts.recurringWeekdaysPreset()
    );
    check(
        "Recurring week save sends one atomic owner-scoped PUT with all day times",
        () => {
            const request = requests.at(-1);
            assert.equal(request.method, "PUT");
            assert.equal(
                new URL(request.url).pathname,
                "/api/v1/locations/room-a/availability/rules"
            );
            assert.equal(request.body.rules.length, 5);
        }
    );
    const recurringEditor = (
        await server.ssrLoadModule(
            "/src/features/availability/RecurringWeekEditor.tsx"
        )
    ).default;
    const recurringHtml = wrap(
        React.createElement(recurringEditor, {
            kind: "staff",
            ownerId: "staff-a",
            initialRules: []
        })
    );
    check(
        "Recurring hours render seven accessible day dots and quick presets",
        () => {
            assert.equal(
                (
                    recurringHtml.match(
                        /aria-label="[A-Z][a-z]+ recurring hours"/g
                    ) ?? []
                ).length,
                7
            );
            assert(recurringHtml.includes("Weekdays 09–17"));
            assert(recurringHtml.includes("Save recurring hours"));
        }
    );
    const directBookingHtml = wrap(
        React.createElement(editor, {
            initialCustomerId: "emma",
            onSubmit: async () => {},
            onCancel() {}
        })
    );
    check(
        "Customer-originated booking prefills the customer and keeps full assignment selection",
        () => {
            assert(directBookingHtml.includes('value="Emma Wilson"'));
            assert(directBookingHtml.includes("Customer context"));
            assert(directBookingHtml.includes("Booking service"));
            assert(customerDetailHtml.includes("New appointment"));
        }
    );
    check(
        "Appointment notes have a keyboard-accessible multiline scroll region",
        () => {
            assert(detailHtml.includes('role="region"'));
            assert(detailHtml.includes("hover or focus to scroll"));
        }
    );
    const entityNames = await server.ssrLoadModule(
        "/src/features/assignments/entityLabels.ts"
    );
    check(
        "Duplicate names remain selectable and show stable identifying details",
        () => {
            const people = [
                {
                    id: "staff-00000001",
                    name: "Robert",
                    email: "one@example.test"
                },
                { id: "staff-00000002", name: " robert " }
            ];
            assert.notEqual(
                entityNames.entityLabel(people[0], people),
                entityNames.entityLabel(people[1], people)
            );
            assert(
                entityNames
                    .entityLabel(people[0], people)
                    .includes("one@example.test")
            );
            assert(
                entityNames.entityLabel(people[1], people).includes("00000002")
            );
            assert.equal(
                entityNames.entityLabel(people[0], [people[0]]),
                "Robert"
            );
        }
    );
    check(
        "Create flow always includes a visible availability prerequisite",
        () => {
            assert(
                directBookingHtml.includes('aria-label="Booking availability"')
            );
            assert(
                directBookingHtml.includes(
                    "Choose a service to see its available times"
                )
            );
        }
    );
    const availabilityKey = [
        "availability",
        "reschedule",
        item.serviceId,
        item.id,
        date,
        item.staffId,
        item.locationId,
        ""
    ];
    const stableEditor = () =>
        wrap(
            React.createElement(
                IsRestoringProvider,
                { value: true },
                React.createElement(editor, {
                    appointment: item,
                    onSubmit: async () => {},
                    onCancel() {}
                })
            )
        );
    client.setQueryData(availabilityKey, [
        { ...base, start: item.startAt, end: item.endAt }
    ]);
    check(
        "Rescheduling defaults to tenant today and exposes returned booking combinations",
        () => {
            const html = stableEditor();
            assert(html.includes(`value="${date}"`));
            assert(html.includes("Available booking combination"));
            assert(html.includes("Sofia"));
            assert(html.includes("Room 1"));
        }
    );
    client.setQueryData(availabilityKey, []);
    check(
        "Empty availability explicitly explains how to change the combination",
        () => {
            const html = stableEditor();
            assert(
                html.includes(
                    "No availability for this date and assignment combination"
                )
            );
            assert(
                !html.includes('aria-label="Available booking combination"')
            );
        }
    );
    client
        .getQueryCache()
        .find({ queryKey: availabilityKey, exact: true })
        .setState({
            status: "error",
            error: new Error("Connection unavailable")
        });
    check("Availability failures expose an error and retry action", () => {
        const html = stableEditor();
        assert(html.includes("Connection unavailable"));
        assert(html.includes("Retry"));
    });
    check("Dashboard create route opens the booking editor immediately", () => {
        const html = renderToStaticMarkup(
            React.createElement(
                QueryClientProvider,
                { client },
                React.createElement(
                    MemoryRouter,
                    { initialEntries: ["/appointments?openCreate=1"] },
                    React.createElement(calendarPage)
                )
            )
        );
        assert(html.includes("Create appointment"));
        assert(html.includes("Booking availability"));
    });
    check("Calendar day counts use the singular for one appointment", () => {
        assert(calendarHtml.includes("1 appointment</span>"));
        assert(!calendarHtml.includes("1 appointments</span>"));
    });
    check("Service inputs enforce the backend size and numeric limits", () => {
        assert.match(formHtml, /maxLength="200"/i);
        assert.match(formHtml, /maxLength="2000"/i);
        assert.match(formHtml, /max="1440"/);
        assert.match(formHtml, /max="9999999999.99"/);
        assert.match(formHtml, /pattern="\[A-Z\]\{3\}"/);
    });
    console.log(
        `Passed ${results.length} frontend API, identity, timezone, and render checks.`
    );
} finally {
    await server.close();
}
