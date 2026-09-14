package com.example.booking.tenantdata;

import com.example.booking.tenant.TenantContext;
import com.example.booking.tenantdata.appointment.*;
import com.example.booking.tenantdata.availability.*;
import com.example.booking.tenantdata.location.*;
import com.example.booking.tenantdata.staff.*;
import com.example.booking.tenantdata.resource.*;
import com.example.booking.tenantdata.service.*;
import com.example.booking.tenantdata.settings.TenantSettingsService;
import com.example.booking.tenantdata.management.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.example.booking.tenantdata.service.AssignmentRequirement.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = "app.security.jwt-enabled=false")
@AutoConfigureMockMvc
@WithMockUser(roles = "TENANT_ADMIN")
class BackendAssignmentIntegrationTest {
    @Autowired @Qualifier("tenantTransactionManager") PlatformTransactionManager transactions;
    @Autowired CustomerRepository customers;
    @Autowired StaffMemberRepository staffRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired BookableResourceRepository resourceRepository;
    @Autowired ServiceOfferingRepository offeringRepository;
    @Autowired ServiceOfferingService offeringService;
    @Autowired StaffMemberService staffService;
    @Autowired LocationService locationService;
    @Autowired BookableResourceService resourceService;
    @Autowired AvailabilityScheduleService schedules;
    @Autowired AvailabilityService availability;
    @Autowired AppointmentService bookings;
    @Autowired AppointmentRepository appointments;
    @Autowired TenantSettingsService settings;
    @Autowired StaffWeekScheduleService weekSchedules;
    @Autowired MockMvc mvc;

    @Test void sameStaffCannotOverlap() { inTenant(() -> rejectOverlap(0)); }
    @Test void sameLocationCannotOverlap() { inTenant(() -> rejectOverlap(1)); }
    @Test void sameResourceCannotOverlap() { inTenant(() -> rejectOverlap(2)); }

    private void rejectOverlap(int dimension) {
        Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
        book(f, 0, 0, 0, 10);
        var error = assertThrows(ResponseStatusException.class,
                () -> book(f, dimension == 0 ? 0 : 1, dimension == 1 ? 0 : 1, dimension == 2 ? 0 : 1, 10));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test void databaseRejectsSameStaffOverlapWithoutServicePrecheck() {
        inTenant(() -> databaseRejectsOverlap(0));
    }
    @Test void databaseRejectsSameLocationOverlapWithoutServicePrecheck() {
        inTenant(() -> databaseRejectsOverlap(1));
    }
    @Test void databaseRejectsSameResourceOverlapWithoutServicePrecheck() {
        inTenant(() -> databaseRejectsOverlap(2));
    }
    private void databaseRejectsOverlap(int dimension) {
        Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
        book(f, 0, 0, 0, 10);
        var customer = customers.findById(f.customer()).orElseThrow();
        var offering = offeringRepository.findByIdWithResources(f.service()).orElseThrow();
        var staff = staffRepository.findById(f.staff().get(dimension == 0 ? 0 : 1)).orElseThrow();
        var location = locationRepository.findById(f.locations().get(dimension == 1 ? 0 : 1)).orElseThrow();
        var resource = resourceRepository.findById(f.resources().get(dimension == 2 ? 0 : 1)).orElseThrow();
        var collision = new Appointment(customer, offering, staff, location, resource, at(f, 10), at(f, 11), null);
        assertThrows(DataIntegrityViolationException.class, () -> appointments.saveAndFlush(collision));
    }

    @Test void differentAssignmentsCanOverlap() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            assertNotEquals(book(f, 0, 0, 0, 10).id(), book(f, 1, 1, 1, 10).id());
        });
    }

    @Test void cancellationFreesAllAssignmentCapacity() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            var booking = book(f, 0, 0, 0, 10);
            bookings.cancel(booking.id());
            assertTrue(slots(f, 0, 0, 0).stream().anyMatch(s -> s.start().equals(at(f, 10))));
            assertNotEquals(booking.id(), book(f, 0, 0, 0, 10).id());
        });
    }

    @Test void reschedulingIgnoresItselfAndChecksRequiredAssignments() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            var booked = book(f, 0, 0, 0, 10);
            var moved = bookings.reschedule(booked.id(), new RescheduleAppointmentRequest(
                    f.staff().getFirst(), f.locations().getFirst(), f.resources().getFirst(), at(f, 10).plusMinutes(15)));
            assertEquals(at(f, 10).plusMinutes(15), moved.startAt());
            var same = bookings.reschedule(booked.id(), new RescheduleAppointmentRequest(
                    f.staff().getFirst(), f.locations().getFirst(), f.resources().getFirst(), moved.startAt()));
            assertEquals(moved.startAt(), same.startAt());
            var error = assertThrows(ResponseStatusException.class, () -> bookings.reschedule(booked.id(),
                    new RescheduleAppointmentRequest(null, f.locations().getFirst(), f.resources().getFirst(), at(f, 12))));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        });
    }

    @Test void blockedExceptionsWinForEachNativeOwner() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            for (AvailabilityOwnerType type : AvailabilityOwnerType.values()) {
                UUID id = owner(f, type);
                var open = schedules.createException(type, id, exception(f, 10, 11, true));
                var blocked = schedules.createException(type, id, exception(f, 10, 11, false));
                assertTrue(slots(f, 0, 0, 0).stream().noneMatch(s -> s.start().isBefore(at(f, 11)) && s.end().isAfter(at(f, 10))));
                var error = assertThrows(ResponseStatusException.class, () -> book(f, 0, 0, 0, 10));
                assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
                schedules.deleteException(type, id, open.id());
                schedules.deleteException(type, id, blocked.id());
            }
        });
    }

    @Test void extraAvailabilityCanOpenAnOtherwiseClosedDay() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            for (AvailabilityOwnerType type : AvailabilityOwnerType.values()) {
                UUID id = owner(f, type);
                for (var rule : schedules.findRules(type, id)) schedules.deleteRule(type, id, rule.id());
                schedules.createException(type, id, exception(f, 10, 11, true));
            }
            var slots = slots(f, 0, 0, 0);
            assertEquals(1, slots.size());
            assertEquals(at(f, 10), slots.getFirst().start());
            book(f, 0, 0, 0, 10);
        });
    }

    @Test void filteredAvailabilityReturnsOnlyRequestedAssignments() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            var slots = slots(f, 1, 0, 1);
            assertEquals(29, slots.size());
            assertTrue(slots.stream().allMatch(s -> s.staffId().equals(f.staff().get(1))
                    && s.locationId().equals(f.locations().getFirst()) && s.resourceId().equals(f.resources().get(1))));
            mvc.perform(get("/api/v1/availability").with(authenticated("TENANT_ADMIN"))
                    .param("serviceId", f.service().toString()).param("from", f.date().toString()).param("to", f.date().toString())
                    .param("staffId", f.staff().get(1).toString()).param("locationId", f.locations().getFirst().toString())
                    .param("resourceId", f.resources().get(1).toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(29))
                    .andExpect(jsonPath("$[0].staffId").value(f.staff().get(1).toString()))
                    .andExpect(jsonPath("$[0].locationId").value(f.locations().getFirst().toString()))
                    .andExpect(jsonPath("$[0].resourceId").value(f.resources().get(1).toString()));
        });
    }

    @Test void unfilteredAvailabilityEnumeratesCompleteRequiredAndOptionalCombinations() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            var slots = availability.findAvailability(f.service(), f.date(), f.date(), null, null, null);
            assertEquals(8 * 29, slots.size());
            assertTrue(slots.stream().allMatch(s -> s.staffId() != null && s.locationId() != null && s.resourceId() != null));
            Fixture optional = fixture(REQUIRED, REQUIRED, OPTIONAL);
            var optionalSlots = availability.findAvailability(optional.service(), optional.date(), optional.date(), null, null, null);
            assertEquals(12 * 29, optionalSlots.size());
            assertEquals(4 * 29, optionalSlots.stream().filter(s -> s.resourceId() == null).count());
        });
    }

    @Test void rejectsMissingForbiddenIneligibleAndInactiveAssignments() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, FORBIDDEN, FORBIDDEN);
            assertEquals(2 * 29, availability.findAvailability(f.service(), f.date(), f.date(), null, null, null).size());
            assertBadRequest(() -> bookings.create(new CreateAppointmentRequest(f.customer(), f.service(), null, null, null, at(f, 10), null)));
            assertBadRequest(() -> bookings.create(new CreateAppointmentRequest(f.customer(), f.service(), f.staff().getFirst(),
                    f.locations().getFirst(), null, at(f, 10), null)));
            assertBadRequest(() -> availability.findAvailability(f.service(), f.date(), f.date(), null, f.locations().getFirst(), null));
            var unrelatedStaff = staffService.create(new CreateAssignmentRequest("Unrelated"));
            assertBadRequest(() -> bookings.create(new CreateAppointmentRequest(f.customer(), f.service(), unrelatedStaff.id(), null, null, at(f, 10), null)));
            staffService.setActive(f.staff().getFirst(), false);
            assertBadRequest(() -> bookings.create(new CreateAppointmentRequest(f.customer(), f.service(), f.staff().getFirst(), null, null, at(f, 10), null)));
            staffService.setActive(f.staff().get(1), false);
            assertTrue(availability.findAvailability(f.service(), f.date(), f.date(), null, null, null).isEmpty());
        });
    }

    @Test void nativeManagementRoutesEditDeactivateAndEnforceOwnership() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            mvc.perform(post("/api/v1/staff").with(authenticated("TENANT_ADMIN")).contentType("application/json").content("{\"name\":\"Sofia\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true));
            mvc.perform(post("/api/v1/locations").with(authenticated("TENANT_ADMIN")).contentType("application/json").content("{\"name\":\"Room 1\"}"))
                    .andExpect(status().isCreated());
            for (String path : List.of("staff", "locations", "resources")) {
                AvailabilityOwnerType type = AvailabilityOwnerType.fromPath(path);
                UUID id = owner(f, type);
                var rule = schedules.findRules(type, id).getFirst();
                mvc.perform(get("/api/v1/" + path).with(authenticated("TENANT_ADMIN"))).andExpect(status().isOk());
                String assignmentUpdate = type == AvailabilityOwnerType.RESOURCE
                        ? "{\"name\":\"Renamed resource\",\"type\":\"EQUIPMENT\",\"active\":true}"
                        : "{\"name\":\"Renamed assignment\",\"active\":true}";
                mvc.perform(put("/api/v1/" + path + "/" + id).with(authenticated("TENANT_ADMIN"))
                        .contentType("application/json").content(assignmentUpdate))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
                mvc.perform(patch("/api/v1/" + path + "/" + id + "/active").with(authenticated("TENANT_ADMIN"))
                        .contentType("application/json").content("{\"active\":false}"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
                mvc.perform(patch("/api/v1/" + path + "/" + id + "/active").with(authenticated("TENANT_ADMIN"))
                        .contentType("application/json").content("{\"active\":true}"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
                String rulePath = "/api/v1/" + path + "/" + id + "/availability/rules/" + rule.id();
                String update = "{\"dayOfWeek\":\"" + f.date().getDayOfWeek() + "\",\"startTime\":\"09:00\",\"endTime\":\"15:00\",\"active\":false}";
                mvc.perform(put(rulePath).with(authenticated("TENANT_ADMIN")).contentType("application/json").content(update))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
                mvc.perform(delete(rulePath).with(authenticated("TENANT_ADMIN")).with(authenticated("CUSTOMER")))
                        .andExpect(status().isForbidden());
                TenantContext.setTenantId("tenant-a");
                UUID otherId = switch(type) { case STAFF -> f.staff().get(1); case LOCATION -> f.locations().get(1); case RESOURCE -> f.resources().get(1); };
                var ownershipError = assertThrows(ResponseStatusException.class, () -> schedules.deleteRule(type, otherId, rule.id()));
                assertEquals(HttpStatus.NOT_FOUND, ownershipError.getStatusCode());
                var exception = schedules.createException(type, id, exception(f, 10, 11, false));
                schedules.updateException(type, id, exception.id(), exception(f, 12, 13, true));
                assertTrue(schedules.findExceptions(type, id).stream().anyMatch(e -> e.id().equals(exception.id()) && e.available()));
                schedules.deleteException(type, id, exception.id());
                assertTrue(schedules.findExceptions(type, id).stream().noneMatch(e -> e.id().equals(exception.id())));
            }
            mvc.perform(post("/api/v1/resources").with(authenticated("TENANT_ADMIN")).contentType("application/json")
                    .content("{\"name\":\"Legacy staff\",\"type\":\"STAFF\",\"active\":true}"))
                    .andExpect(status().isBadRequest());
        });
    }

    @Test void requiredAssignmentsCannotBeOmittedForAnyCategory() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            for (int dimension = 0; dimension < 3; dimension++) {
                final int d = dimension;
                assertBadRequest(() -> bookings.create(new CreateAppointmentRequest(f.customer(), f.service(),
                        d == 0 ? null : f.staff().getFirst(), d == 1 ? null : f.locations().getFirst(),
                        d == 2 ? null : f.resources().getFirst(), at(f, 10), null)));
            }
            var booked = book(f, 0, 0, 0, 10);
            for (int dimension = 0; dimension < 3; dimension++) {
                final int d = dimension;
                assertBadRequest(() -> bookings.reschedule(booked.id(), new RescheduleAppointmentRequest(
                        d == 0 ? null : f.staff().getFirst(), d == 1 ? null : f.locations().getFirst(),
                        d == 2 ? null : f.resources().getFirst(), at(f, 12))));
            }
        });
    }

    @Test void nativeAvailabilityRoutesCreateEditAndDeleteSchedules() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            for (String path : List.of("staff", "locations", "resources")) {
                var type = AvailabilityOwnerType.fromPath(path);
                UUID id = owner(f, type);
                String root = "/api/v1/" + path + "/" + id + "/availability";
                String body = "{\"dayOfWeek\":\"" + f.date().getDayOfWeek() + "\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}";
                mvc.perform(post(root + "/rules").with(authenticated("TENANT_ADMIN"))
                        .contentType("application/json").content(body)).andExpect(status().isCreated());
                TenantContext.setTenantId("tenant-a");
                var addedRule = schedules.findRules(type, id).stream().filter(r -> r.startTime().equals(LocalTime.of(10, 0))).findFirst().orElseThrow();
                mvc.perform(delete(root + "/rules/" + addedRule.id()).with(authenticated("TENANT_ADMIN")))
                        .andExpect(status().isNoContent());
                body = "{\"startAt\":\"" + f.date() + "T10:00:00\",\"endAt\":\"" + f.date() + "T11:00:00\",\"available\":false}";
                mvc.perform(post(root + "/exceptions").with(authenticated("TENANT_ADMIN"))
                        .contentType("application/json").content(body)).andExpect(status().isCreated());
                TenantContext.setTenantId("tenant-a");
                var exception = schedules.findExceptions(type, id).getFirst();
                body = "{\"startAt\":\"" + f.date() + "T12:00:00\",\"endAt\":\"" + f.date() + "T13:00:00\",\"available\":true}";
                mvc.perform(put(root + "/exceptions/" + exception.id()).with(authenticated("TENANT_ADMIN"))
                        .contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
                mvc.perform(get(root + "/exceptions").with(authenticated("TENANT_ADMIN")))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
                mvc.perform(delete(root + "/exceptions/" + exception.id()).with(authenticated("TENANT_ADMIN")))
                        .andExpect(status().isNoContent());
                TenantContext.setTenantId("tenant-a");
                assertTrue(schedules.findExceptions(type, id).isEmpty());
            }
        });
    }

    @Test void serviceManagementRoundTripsEligibilityAndExplicitRequirements() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, OPTIONAL);
            var original = offeringService.findById(f.service());
            assertEquals(REQUIRED, original.staffRequirement());
            assertEquals(new HashSet<>(f.staff()), original.staffIds());
            var updated = offeringService.update(f.service(), new UpdateServiceRequest("Online consultation", null, 30,
                    BigDecimal.TEN, "EUR", true, Set.of(f.staff().getFirst()), Set.of(), Set.of(), REQUIRED, FORBIDDEN, FORBIDDEN));
            assertEquals(FORBIDDEN, updated.locationRequirement());
            assertEquals(FORBIDDEN, offeringService.findById(f.service()).resourceRequirement());
            assertTrue(updated.locationIds().isEmpty());
            assertBadRequest(() -> offeringService.create(new CreateServiceRequest("Incomplete", null, 60, null, "EUR",
                    Set.of(), Set.of(), Set.of(), REQUIRED, FORBIDDEN, FORBIDDEN)));
            assertBadRequest(() -> offeringService.create(new CreateServiceRequest("Contradictory", null, 60, null, "EUR",
                    Set.of(f.staff().getFirst()), Set.of(f.locations().getFirst()), Set.of(), REQUIRED, FORBIDDEN, FORBIDDEN)));
            assertBadRequest(() -> offeringService.create(new CreateServiceRequest("No requirements", null, 60, null, "EUR",
                    Set.of(), Set.of(), Set.of(), OPTIONAL, OPTIONAL, OPTIONAL)));
            String serviceBody = "{\"name\":\"Equipment rental\",\"durationMinutes\":60,\"currency\":\"EUR\","
                    + "\"staffIds\":[],\"locationIds\":[],\"resourceIds\":[\"" + f.resources().getFirst() + "\"],"
                    + "\"staffRequirement\":\"FORBIDDEN\",\"locationRequirement\":\"FORBIDDEN\",\"resourceRequirement\":\"REQUIRED\"}";
            mvc.perform(post("/api/v1/services").with(authenticated("TENANT_ADMIN"))
                    .contentType("application/json").content(serviceBody)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.resourceRequirement").value("REQUIRED"));
            serviceBody = serviceBody.substring(0, serviceBody.length() - 1) + ",\"active\":false}";
            mvc.perform(put("/api/v1/services/" + f.service()).with(authenticated("TENANT_ADMIN"))
                    .contentType("application/json").content(serviceBody)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.staffIds.length()").value(0)).andExpect(jsonPath("$.resourceRequirement").value("REQUIRED"));
            TenantContext.setTenantId("tenant-a");
            assertTrue(availability.findAvailability(f.service(), f.date(), f.date(), null, null, null).isEmpty());
            mvc.perform(post("/api/v1/services").with(authenticated("TENANT_ADMIN")).contentType("application/json")
                    .content("{\"name\":\"Missing policy\",\"durationMinutes\":60}"))
                    .andExpect(status().isBadRequest());
        });
    }

    @Test void calendarFiltersNativeStaffAndLocation() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            var first = book(f, 0, 0, 0, 10);
            book(f, 1, 1, 1, 10);
            var filtered = bookings.findCalendar(f.date(), f.date(), null, f.staff().getFirst(),
                    f.locations().getFirst(), null, f.service(), null);
            assertEquals(1, filtered.size());
            assertEquals(first.id(), filtered.getFirst().id());
            mvc.perform(get("/api/v1/appointments/calendar").with(authenticated("TENANT_ADMIN"))
                    .param("from", f.date().toString()).param("to", f.date().toString())
                    .param("staffId", f.staff().getFirst().toString()).param("locationId", f.locations().getFirst().toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].staffId").value(f.staff().getFirst().toString()))
                    .andExpect(jsonPath("$[0].locationId").value(f.locations().getFirst().toString()));
        });
    }

    @Test void rescheduleSlotsExcludeOnlyTheAppointmentBeingEdited() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            var first = book(f, 0, 0, 0, 10);
            book(f, 0, 0, 0, 12);
            assertTrue(slots(f, 0, 0, 0).stream().noneMatch(s -> s.start().equals(at(f, 10))));
            var editable = bookings.findRescheduleAvailability(first.id(), f.date(), f.date(),
                    f.staff().getFirst(), f.locations().getFirst(), f.resources().getFirst());
            assertTrue(editable.stream().anyMatch(s -> s.start().equals(at(f, 10))));
            assertTrue(editable.stream().noneMatch(s -> s.start().isBefore(at(f, 13)) && s.end().isAfter(at(f, 12))));
            mvc.perform(get("/api/v1/appointments/" + first.id() + "/availability").with(authenticated("TENANT_ADMIN"))
                    .param("from", f.date().toString()).param("to", f.date().toString())
                    .param("staffId", f.staff().getFirst().toString()).param("locationId", f.locations().getFirst().toString())
                    .param("resourceId", f.resources().getFirst().toString())).andExpect(status().isOk());
            TenantContext.setTenantId("tenant-a");
            bookings.cancel(first.id());
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> bookings.findRescheduleAvailability(first.id(), f.date(), f.date(), null, null, null)).getStatusCode());
        });
    }

    @Autowired com.example.booking.tenantdata.dashboard.DashboardService dashboard;
    @Test void dashboardUsesNativeStaffAndResourceFreeBookingDetails() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, OPTIONAL);
            var customer = customers.findById(f.customer()).orElseThrow();
            var offering = offeringRepository.findByIdWithResources(f.service()).orElseThrow();
            var staff = staffRepository.findById(f.staff().getFirst()).orElseThrow();
            var location = locationRepository.findById(f.locations().getFirst()).orElseThrow();
            OffsetDateTime start = LocalDate.now(settings.getZoneId()).atTime(10, 0).atZone(settings.getZoneId()).toOffsetDateTime();
            var appointment = appointments.saveAndFlush(new Appointment(customer, offering, staff, location, null, start, start.plusHours(1), null));
            var summary = dashboard.getSummary();
            var teamMember = summary.team().stream().filter(member -> member.staffId().equals(staff.getId())).findFirst().orElseThrow();
            assertEquals(1, teamMember.todaysBookings());
            var card = summary.todaysAppointments().stream().filter(item -> item.id().equals(appointment.getId())).findFirst().orElseThrow();
            assertEquals(staff.getId(), card.staffId());
            assertEquals(location.getId(), card.locationId());
            assertNull(card.resourceId());
        });
    }

    @Test void locationDetailsPersistAndNameOnlyUpdatesKeepAddress() {
        inTenant(() -> {
            var created = locationService.create(new LocationRequest("Studio", "Ground floor", "Main Street 10", "Helsinki", "00100", "FI", "+358 555", true));
            assertEquals("Main Street 10", locationRepository.findById(created.id()).orElseThrow().getAddressLine());
            mvc.perform(put("/api/v1/locations/" + created.id()).with(authenticated("TENANT_ADMIN"))
                    .contentType("application/json").content("{\"name\":\"Main studio\",\"active\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.addressLine").value("Main Street 10"));
            mvc.perform(get("/api/v1/locations/" + created.id()).with(authenticated("STAFF")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Main studio"))
                    .andExpect(jsonPath("$.countryCode").value("FI"));
            mvc.perform(put("/api/v1/locations/" + created.id()).with(authenticated("TENANT_ADMIN"))
                    .contentType("application/json").content("{\"name\":\"Main studio\",\"addressLine\":\"\",\"active\":false}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.addressLine").value(""))
                    .andExpect(jsonPath("$.active").value(false));
            mvc.perform(post("/api/v1/locations").with(authenticated("TENANT_ADMIN"))
                    .contentType("application/json").content("{\"name\":\"Bad country\",\"countryCode\":\"FIN\"}"))
                    .andExpect(status().isBadRequest());
        });
    }

    @Test void workspaceSettingsPersistAndRequireAdministrator() {
        inTenant(() -> {
            String update = "{\"timeZone\":\"Europe/Helsinki\",\"businessName\":\"My Studio\",\"contactEmail\":\"hello@example.com\",\"contactPhone\":\"123\",\"defaultCurrency\":\"USD\",\"slotIntervalMinutes\":30,\"minimumNoticeMinutes\":60,\"bookingHorizonDays\":90,\"calendarStartHour\":7,\"calendarEndHour\":20,\"weekStartsOn\":0,\"defaultAppointmentStatus\":\"CONFIRMED\"}";
            mvc.perform(put("/api/v1/settings").with(authenticated("STAFF")).contentType("application/json").content(update))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/settings").with(authenticated("TENANT_ADMIN")).contentType("application/json").content(update))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.businessName").value("My Studio"));
            mvc.perform(get("/api/v1/settings").with(authenticated("STAFF")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.slotIntervalMinutes").value(30))
                    .andExpect(jsonPath("$.defaultCurrency").value("USD"))
                    .andExpect(jsonPath("$.weekStartsOn").value(0));
            assertEquals("hello@example.com", settings.getSettings().contactEmail());
            assertEquals("CONFIRMED", settings.getSettings().defaultAppointmentStatus());
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            assertEquals(AppointmentStatus.CONFIRMED, book(f, 0, 0, 0, 10).status());
            settings.update(new com.example.booking.tenantdata.settings.UpdateTenantSettingsRequest(settings.getZoneId().getId(),
                    null, null, null, null, null, null, null, null, null, null, "PENDING"));
            assertEquals(AppointmentStatus.PENDING, book(f, 1, 1, 1, 11).status());
        });
    }

    @Test void savedBookingPolicyControlsSlotSpacingAndRejectsCreateAndRescheduleOutsideHorizon() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            var appointment = book(f, 0, 0, 0, 10);
            settings.update(new com.example.booking.tenantdata.settings.UpdateTenantSettingsRequest(settings.getZoneId().getId(),
                    null, null, null, null, 30, 0, 365, null, null, null, null));
            var offered = slots(f, 1, 1, 1);
            assertTrue(offered.stream().anyMatch(slot -> slot.start().equals(at(f, 11))));
            assertFalse(offered.stream().anyMatch(slot -> slot.start().equals(at(f, 11).plusMinutes(15))));
            settings.update(new com.example.booking.tenantdata.settings.UpdateTenantSettingsRequest(settings.getZoneId().getId(),
                    null, null, null, null, null, null, 3, null, null, null, null));
            assertTrue(slots(f, 1, 1, 1).isEmpty());
            assertBadRequest(() -> book(f, 1, 1, 1, 11));
            assertBadRequest(() -> bookings.reschedule(appointment.id(), new RescheduleAppointmentRequest(f.staff().getFirst(), f.locations().getFirst(), f.resources().getFirst(), at(f, 12))));
        });
    }

    @Test void minimumNoticeRejectsStartsAndInvalidSettingsAreRejected() {
        inTenant(() -> {
            settings.update(new com.example.booking.tenantdata.settings.UpdateTenantSettingsRequest(settings.getZoneId().getId(),
                    null, null, null, null, null, 60, null, null, null, null, null));
            assertBadRequest(() -> settings.validateBookingStart(OffsetDateTime.now().plusMinutes(10)));
            mvc.perform(put("/api/v1/settings").with(authenticated("TENANT_ADMIN")).contentType("application/json")
                    .content("{\"timeZone\":\"Europe/Helsinki\",\"slotIntervalMinutes\":0}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/v1/settings").with(authenticated("TENANT_ADMIN")).contentType("application/json")
                    .content("{\"timeZone\":\"Europe/Helsinki\",\"calendarStartHour\":20,\"calendarEndHour\":8}"))
                    .andExpect(status().isBadRequest());
        });
    }

    @Test void staffContactDetailsPersistAndNameOnlyUpdatesPreserveThem() {
        inTenant(() -> {
            var member = staffService.create(new StaffMemberRequest("Sofia", "sofia@example.com", "+358 555", true, true, Set.of()));
            mvc.perform(put("/api/v1/staff/" + member.id()).with(authenticated("TENANT_ADMIN"))
                    .contentType("application/json").content("{\"name\":\"Sofia N\",\"active\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("sofia@example.com"))
                    .andExpect(jsonPath("$.phone").value("+358 555"));
            mvc.perform(get("/api/v1/staff/" + member.id()).with(authenticated("STAFF")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Sofia N"))
                    .andExpect(jsonPath("$.freeAgent").value(true));
            mvc.perform(post("/api/v1/staff").with(authenticated("TENANT_ADMIN"))
                    .contentType("application/json").content("{\"name\":\"Bad email\",\"email\":\"invalid\"}"))
                    .andExpect(status().isBadRequest());
        });
    }

    @Test void restrictedStaffLocationPairsAreFilteredAndRejectedAtBookingAndReschedule() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED);
            var member = staffService.findById(f.staff().getFirst());
            staffService.update(member.id(), new StaffMemberRequest(member.name(), member.email(), member.phone(), true, false, Set.of(f.locations().getFirst())));
            var all = availability.findAvailability(f.service(), f.date(), f.date(), null, null, null);
            assertTrue(all.stream().anyMatch(slot -> f.staff().getFirst().equals(slot.staffId()) && f.locations().getFirst().equals(slot.locationId())));
            assertFalse(all.stream().anyMatch(slot -> f.staff().getFirst().equals(slot.staffId()) && f.locations().get(1).equals(slot.locationId())));
            assertTrue(all.stream().anyMatch(slot -> f.staff().get(1).equals(slot.staffId()) && f.locations().get(1).equals(slot.locationId())));
            assertBadRequest(() -> book(f, 0, 1, 0, 10));
            var booked = book(f, 0, 0, 0, 10);
            assertBadRequest(() -> bookings.reschedule(booked.id(), new RescheduleAppointmentRequest(f.staff().getFirst(), f.locations().get(1), f.resources().getFirst(), at(f, 12))));
            staffService.update(member.id(), new StaffMemberRequest(member.name(), null, null, true, true, Set.of()));
            assertTrue(slots(f, 0, 1, 0).stream().anyMatch(slot -> slot.start().equals(at(f, 12))));
        });
    }

    @Test void staffNeedsLocationsWhenRestrictedButCanStillDoLocationFreeServices() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, FORBIDDEN, FORBIDDEN);
            var member = staffService.findById(f.staff().getFirst());
            assertBadRequest(() -> staffService.update(member.id(), new StaffMemberRequest(member.name(), null, null, true, false, Set.of())));
            staffService.update(member.id(), new StaffMemberRequest(member.name(), null, null, true, false, Set.of(f.locations().getFirst())));
            var online = bookings.create(new CreateAppointmentRequest(f.customer(), f.service(), member.id(), null, null, at(f, 10), null));
            assertEquals(member.id(), online.staffId()); assertNull(online.locationId());
            assertBadRequest(() -> staffService.update(member.id(), new StaffMemberRequest(member.name(), null, null, true, false, Set.of(UUID.randomUUID()))));
        });
    }

    @Test void staffRemovalArchivesHistoryAndRemovesFutureEligibility() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED); var booked = book(f, 0, 0, 0, 10);
            String name = staffService.findById(f.staff().getFirst()).name();
            mvc.perform(delete("/api/v1/staff/" + f.staff().getFirst()).with(authenticated("TENANT_ADMIN"))).andExpect(status().isNoContent());
            assertTrue(staffService.findAll().stream().noneMatch(member -> member.id().equals(f.staff().getFirst())));
            var archived = staffRepository.findById(f.staff().getFirst()).orElseThrow();
            assertTrue(archived.isRemoved()); assertFalse(archived.isActive());
            var calendar = bookings.findCalendar(f.date(), f.date(), null, f.staff().getFirst(), null, null, null, null);
            assertTrue(calendar.stream().anyMatch(item -> item.id().equals(booked.id()) && name.equals(item.staffName())));
            assertTrue(offeringRepository.findByIdWithResources(f.service()).orElseThrow().getStaff().stream().noneMatch(member -> member.getId().equals(archived.getId())));
            mvc.perform(get("/api/v1/staff/" + archived.getId()).with(authenticated("TENANT_ADMIN"))).andExpect(status().isNotFound());
        });
    }

    @Test void weeklyRotaOverridesOneWeekAndResetRestoresRecurringHours() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED); LocalDate week = f.date().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            schedules.createRule(AvailabilityOwnerType.STAFF, f.staff().getFirst(), new AvailabilityRuleRequest(f.date().getDayOfWeek(), LocalTime.of(10, 0), LocalTime.of(14, 0)));
            var inherited = weekSchedules.find(f.staff().getFirst(), week);
            assertFalse(inherited.overridden()); assertEquals(1, inherited.shifts().size());
            assertEquals(LocalTime.of(9, 0), inherited.shifts().getFirst().startTime());
            assertEquals(LocalTime.of(17, 0), inherited.shifts().getFirst().endTime());
            var saved = weekSchedules.save(f.staff().getFirst(), week, new StaffWeekScheduleRequest(List.of(new StaffWeekScheduleRequest.Shift(f.date(), LocalTime.of(11, 0), LocalTime.of(14, 0)))));
            assertTrue(saved.overridden()); assertEquals(1, saved.shifts().size());
            assertFalse(slots(f, 0, 0, 0).stream().anyMatch(slot -> slot.start().equals(at(f, 10))));
            assertTrue(slots(f, 0, 0, 0).stream().anyMatch(slot -> slot.start().equals(at(f, 11))));
            LocalDate following = f.date().plusDays(7);
            assertTrue(availability.findAvailability(f.service(), following, following, f.staff().getFirst(), f.locations().getFirst(), f.resources().getFirst())
                    .stream().anyMatch(slot -> slot.start().atZoneSameInstant(settings.getZoneId()).getHour() == 10));
            assertTrue(schedules.findExceptions(AvailabilityOwnerType.STAFF, f.staff().getFirst()).isEmpty());
            schedules.createException(AvailabilityOwnerType.STAFF, f.staff().getFirst(), exception(f, 12, 13, false));
            assertFalse(slots(f, 0, 0, 0).stream().anyMatch(slot -> slot.start().equals(at(f, 12))));
            weekSchedules.reset(f.staff().getFirst(), week);
            assertFalse(weekSchedules.find(f.staff().getFirst(), week).overridden());
            assertTrue(slots(f, 0, 0, 0).stream().anyMatch(slot -> slot.start().equals(at(f, 10))));
            assertFalse(slots(f, 0, 0, 0).stream().anyMatch(slot -> slot.start().equals(at(f, 12))));
        });
    }

    @Test void weeklyRotaRejectsOverlapsWithoutReplacingPreviouslySavedShifts() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED); LocalDate week = f.date().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            var first = new StaffWeekScheduleRequest.Shift(f.date(), LocalTime.of(10, 0), LocalTime.of(12, 0));
            weekSchedules.save(f.staff().getFirst(), week, new StaffWeekScheduleRequest(List.of(first)));
            assertBadRequest(() -> weekSchedules.save(f.staff().getFirst(), week, new StaffWeekScheduleRequest(List.of(first,
                    new StaffWeekScheduleRequest.Shift(f.date(), LocalTime.of(11, 0), LocalTime.of(13, 0))))));
            assertEquals(List.of(first), weekSchedules.find(f.staff().getFirst(), week).shifts());
            assertBadRequest(() -> weekSchedules.save(f.staff().getFirst(), week, new StaffWeekScheduleRequest(List.of(
                    new StaffWeekScheduleRequest.Shift(week.plusDays(7), LocalTime.of(9, 0), LocalTime.of(17, 0))))));
            assertEquals(List.of(first), weekSchedules.find(f.staff().getFirst(), week).shifts());
        });
    }

    @Test void weeklyRotaRoutesSupportAllDaysOffAndRejectCustomerAccess() {
        inTenant(() -> {
            Fixture f = fixture(REQUIRED, REQUIRED, REQUIRED); LocalDate week = f.date().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            String url = "/api/v1/staff/" + f.staff().getFirst() + "/schedule";
            mvc.perform(put(url).param("weekStart", week.toString()).with(authenticated("STAFF")).contentType("application/json").content("{\"shifts\":[]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.overridden").value(true)).andExpect(jsonPath("$.shifts").isEmpty());
            assertTrue(slots(f, 0, 0, 0).isEmpty());
            mvc.perform(get(url).param("weekStart", week.toString()).with(authenticated("CUSTOMER"))).andExpect(status().isForbidden());
            mvc.perform(delete(url).param("weekStart", week.toString()).with(authenticated("STAFF"))).andExpect(status().isNoContent());
            assertFalse(slots(f, 0, 0, 0).isEmpty());
            mvc.perform(get("/api/v1/staff/" + UUID.randomUUID() + "/schedule").param("weekStart", week.toString()).with(authenticated("STAFF"))).andExpect(status().isNotFound());
        });
    }

    @Test void resourceDescriptionsPersistAndOmittedUpdatesPreserveThem() {
        inTenant(() -> {
            var resource = resourceService.create(new ResourceRequest("Camera", ResourceType.EQUIPMENT, false, "Mirrorless kit"));
            assertFalse(resource.isActive()); assertEquals("Mirrorless kit", resource.getDescription());
            resourceService.update(resource.getId(), new ResourceRequest("Camera A", ResourceType.EQUIPMENT, true));
            mvc.perform(get("/api/v1/resources/" + resource.getId()).with(authenticated("STAFF")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Mirrorless kit"));
            resourceService.update(resource.getId(), new ResourceRequest("Camera A", ResourceType.EQUIPMENT, true, ""));
            assertEquals("", resourceService.findById(resource.getId()).getDescription());
        });
    }

    private RequestPostProcessor authenticated(String role) {
        return jwt().jwt(token -> token.claim("tenant_id", "tenant-a"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private Fixture fixture(AssignmentRequirement staffPolicy, AssignmentRequirement locationPolicy, AssignmentRequirement resourcePolicy) {
        LocalDate date = LocalDate.now(settings.getZoneId()).plusDays(7);
        var staff = List.of(staffService.create(new CreateAssignmentRequest("Sofia")).id(), staffService.create(new CreateAssignmentRequest("Alex")).id());
        var locations = List.of(locationService.create(new CreateAssignmentRequest("Room 1")).id(), locationService.create(new CreateAssignmentRequest("Room 2")).id());
        var resources = List.of(resourceService.create(new ResourceRequest("Camera A", ResourceType.EQUIPMENT, true)).getId(),
                resourceService.create(new ResourceRequest("Camera B", ResourceType.EQUIPMENT, true)).getId());
        var customer = customers.saveAndFlush(new Customer("Integration", "Customer", UUID.randomUUID() + "@test.local", null));
        var offering = offeringService.create(new CreateServiceRequest("Test service", null, 60, BigDecimal.TEN, "EUR",
                staffPolicy == FORBIDDEN ? Set.of() : new HashSet<>(staff), locationPolicy == FORBIDDEN ? Set.of() : new HashSet<>(locations),
                resourcePolicy == FORBIDDEN ? Set.of() : new HashSet<>(resources), staffPolicy, locationPolicy, resourcePolicy));
        for (UUID id : staff) schedules.createRule(AvailabilityOwnerType.STAFF, id, new AvailabilityRuleRequest(date.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(17, 0)));
        for (UUID id : locations) schedules.createRule(AvailabilityOwnerType.LOCATION, id, new AvailabilityRuleRequest(date.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(17, 0)));
        for (UUID id : resources) schedules.createRule(AvailabilityOwnerType.RESOURCE, id, new AvailabilityRuleRequest(date.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(17, 0)));
        return new Fixture(offering.id(), customer.getId(), staff, locations, resources, date);
    }
    private AppointmentResponse book(Fixture f, int staff, int location, int resource, int hour) {
        return bookings.create(new CreateAppointmentRequest(f.customer(), f.service(), f.staff().get(staff), f.locations().get(location),
                f.resources().get(resource), at(f, hour), null));
    }
    private List<AvailabilitySlotResponse> slots(Fixture f, int staff, int location, int resource) {
        return availability.findAvailability(f.service(), f.date(), f.date(), f.staff().get(staff), f.locations().get(location), f.resources().get(resource));
    }
    private UUID owner(Fixture f, AvailabilityOwnerType type) {
        return switch(type) { case STAFF -> f.staff().getFirst(); case LOCATION -> f.locations().getFirst(); case RESOURCE -> f.resources().getFirst(); };
    }
    private OffsetDateTime at(Fixture f, int hour) { return f.date().atTime(hour, 0).atZone(settings.getZoneId()).toOffsetDateTime(); }
    private AvailabilityExceptionRequest exception(Fixture f, int start, int end, boolean available) {
        return new AvailabilityExceptionRequest(f.date().atTime(start, 0), f.date().atTime(end, 0), available);
    }
    private void assertBadRequest(Runnable action) {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }
    private void inTenant(CheckedAction action) {
        TenantContext.setTenantId("tenant-a");
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                status.setRollbackOnly();
                try { action.run(); } catch (RuntimeException | Error e) { throw e; }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        } finally { TenantContext.clear(); }
    }
    private interface CheckedAction { void run() throws Exception; }
    private record Fixture(UUID service, UUID customer, List<UUID> staff, List<UUID> locations, List<UUID> resources, LocalDate date) {}
}
