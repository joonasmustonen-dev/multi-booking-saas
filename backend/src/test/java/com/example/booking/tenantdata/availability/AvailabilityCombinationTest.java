package com.example.booking.tenantdata.availability;

import com.example.booking.tenantdata.appointment.*;
import com.example.booking.tenantdata.location.Location;
import com.example.booking.tenantdata.resource.*;
import com.example.booking.tenantdata.service.*;
import static com.example.booking.tenantdata.service.AssignmentRequirement.*;
import com.example.booking.tenantdata.settings.TenantSettingsService;
import com.example.booking.tenantdata.staff.StaffMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AvailabilityCombinationTest {

    private final UUID serviceId = UUID.randomUUID();

    private final LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(7);

    private final ServiceOfferingRepository services = mock(
        ServiceOfferingRepository.class
    );

    private final AvailabilityRuleRepository rules = mock(
        AvailabilityRuleRepository.class
    );

    private final AvailabilityExceptionRepository exceptions = mock(
        AvailabilityExceptionRepository.class
    );

    private final AppointmentRepository appointments = mock(
        AppointmentRepository.class
    );

    private final TenantSettingsService settings = mock(
        TenantSettingsService.class
    );

    private final ServiceOffering offering = mock(ServiceOffering.class);

    private final StaffMember staff = staff();

    private final Location location = location();

    private final BookableResource resource = new BookableResource(
        "Equipment",
        ResourceType.EQUIPMENT
    );

    private AvailabilityService service;

    @BeforeEach
    void setup() {
        service = new AvailabilityService(
            services,
            rules,
            exceptions,
            appointments,
            settings
        );

        when(services.findByIdWithResources(serviceId)).thenReturn(
            Optional.of(offering)
        );

        when(settings.getZoneId()).thenReturn(ZoneOffset.UTC);

        when(settings.getSettings()).thenReturn(
            new com.example.booking.tenantdata.settings.TenantSettingsResponse(
                "UTC"
            )
        );

        when(offering.isActive()).thenReturn(true);

        when(staff.isFreeAgent()).thenReturn(true);

        when(offering.getDurationMinutes()).thenReturn(60);

        when(offering.getStaffRequirement()).thenReturn(REQUIRED);

        when(offering.getLocationRequirement()).thenReturn(REQUIRED);

        when(offering.getResourceRequirement()).thenReturn(OPTIONAL);

        when(offering.getStaff()).thenReturn(Set.of(staff));

        when(offering.getLocations()).thenReturn(Set.of(location));

        when(offering.getResources()).thenReturn(Set.of(resource));

        when(rules.findAllByStaff_IdAndActiveTrue(staff.getId())).thenReturn(
            List.of(
                new AvailabilityRule(
                    null,
                    staff,
                    null,
                    date.getDayOfWeek(),
                    LocalTime.of(9, 0),
                    LocalTime.of(12, 0)
                )
            )
        );

        when(
            rules.findAllByLocation_IdAndActiveTrue(location.getId())
        ).thenReturn(
            List.of(
                new AvailabilityRule(
                    null,
                    null,
                    location,
                    date.getDayOfWeek(),
                    LocalTime.of(10, 0),
                    LocalTime.of(13, 0)
                )
            )
        );

        when(rules.findByResourceIdAndActiveTrue(resource.getId())).thenReturn(
            List.of(
                new AvailabilityRule(
                    resource,
                    date.getDayOfWeek(),
                    LocalTime.of(10, 30),
                    LocalTime.of(11, 30)
                )
            )
        );
    }

    @Test
    void intersectsHoursAndOmitsOptionalResource() {
        var slots = find(null, null, null);

        assertEquals(6, slots.size());

        assertEquals(at(10, 0), slots.getFirst().start());

        assertEquals(at(11, 0), slots.getLast().start());

        assertTrue(
            slots
                .stream()
                .allMatch(
                    s ->
                        staff.getId().equals(s.staffId()) &&
                        location.getId().equals(s.locationId())
                )
        );

        assertEquals(
            5,
            slots
                .stream()
                .filter(s -> s.resourceId() == null)
                .count()
        );
    }

    @Test
    void explicitResourceAddsItsScheduleToTheIntersection() {
        var slots = find(staff.getId(), location.getId(), resource.getId());

        assertEquals(1, slots.size());

        assertEquals(at(10, 30), slots.getFirst().start());

        assertEquals(resource.getId(), slots.getFirst().resourceId());
    }

    @Test
    void enumeratesDistinctStaffAndLocationCombinationsAndFiltersThem() {
        StaffMember otherStaff = staff();

        Location otherLocation = location();

        when(offering.getStaff()).thenReturn(Set.of(staff, otherStaff));

        when(offering.getLocations()).thenReturn(
            Set.of(location, otherLocation)
        );

        var staffRules = rules.findAllByStaff_IdAndActiveTrue(staff.getId());

        var locationRules = rules.findAllByLocation_IdAndActiveTrue(
            location.getId()
        );

        when(
            rules.findAllByStaff_IdAndActiveTrue(otherStaff.getId())
        ).thenReturn(staffRules);

        when(
            rules.findAllByLocation_IdAndActiveTrue(otherLocation.getId())
        ).thenReturn(locationRules);

        var slots = find(null, null, null);

        assertEquals(24, slots.size());

        assertEquals(24, new HashSet<>(slots).size());

        assertEquals(
            6,
            find(otherStaff.getId(), otherLocation.getId(), null).size()
        );

        verify(appointments, times(2)).findAssignmentBookingsInRange(
            any(),
            any(),
            any()
        );
    }

    @Test
    void bookingsOnAnyAssignmentBlockTheCombinationButTouchingBoundariesDoNot() {
        for (int dimension = 0; dimension < 3; dimension++) {
            Appointment booking = mock(Appointment.class);

            when(booking.getStaff()).thenReturn(dimension == 0 ? staff : null);

            when(booking.getLocation()).thenReturn(
                dimension == 1 ? location : null
            );

            when(booking.getResource()).thenReturn(
                dimension == 2 ? resource : null
            );

            when(booking.getStartAt()).thenReturn(at(10, 30));

            when(booking.getEndAt()).thenReturn(at(11, 30));

            when(
                appointments.findAssignmentBookingsInRange(any(), any(), any())
            ).thenReturn(List.of(booking));

            assertTrue(find(null, null, resource.getId()).isEmpty());

            when(booking.getEndAt()).thenReturn(at(10, 30));

            when(booking.getStartAt()).thenReturn(at(9, 30));

            assertEquals(1, find(null, null, resource.getId()).size());
        }
    }

    @Test
    void blockedExceptionWinsOverAvailableExceptionForEveryDimension() {
        for (int dimension = 0; dimension < 3; dimension++) {
            var blocked = new AvailabilityException(
                null,
                null,
                null,
                at(10, 30),
                at(11, 30),
                false
            );

            var open = new AvailabilityException(
                null,
                null,
                null,
                at(10, 0),
                at(12, 0),
                true
            );

            when(
                exceptions.findStaffExceptions(any(), any(), any())
            ).thenReturn(dimension == 0 ? List.of(blocked, open) : List.of());

            when(
                exceptions.findLocationExceptions(any(), any(), any())
            ).thenReturn(dimension == 1 ? List.of(blocked, open) : List.of());

            when(
                exceptions.findResourceExceptions(any(), any(), any())
            ).thenReturn(dimension == 2 ? List.of(blocked, open) : List.of());

            assertTrue(find(null, null, resource.getId()).isEmpty());
        }
    }

    @Test
    void openingExceptionCanSupplyHoursWhenRecurringRulesAreAbsent() {
        when(rules.findAllByStaff_IdAndActiveTrue(staff.getId())).thenReturn(
            List.of()
        );

        when(exceptions.findStaffExceptions(any(), any(), any())).thenReturn(
            List.of(
                new AvailabilityException(
                    null,
                    staff,
                    null,
                    at(10, 0),
                    at(11, 0),
                    true
                )
            )
        );

        assertEquals(1, find(null, null, null).size());
    }

    @Test
    void rejectsIneligibleFiltersAndDoesNotMakeInactiveStaffOptional() {
        for (int dimension = 0; dimension < 3; dimension++) {
            UUID unknown = UUID.randomUUID();

            final int d = dimension;

            var error = assertThrows(ResponseStatusException.class, () ->
                find(
                    d == 0 ? unknown : null,
                    d == 1 ? unknown : null,
                    d == 2 ? unknown : null
                )
            );

            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        when(staff.isActive()).thenReturn(false);

        assertTrue(find(null, null, null).isEmpty());
    }

    @Test
    void supportsResourceOnlyAndStaffOnlyServices() {
        when(offering.getStaff()).thenReturn(Set.of());

        when(offering.getLocations()).thenReturn(Set.of());

        when(offering.getStaffRequirement()).thenReturn(FORBIDDEN);

        when(offering.getLocationRequirement()).thenReturn(FORBIDDEN);

        when(offering.getResourceRequirement()).thenReturn(REQUIRED);

        var slots = find(null, null, null);

        assertEquals(1, slots.size());

        assertNull(slots.getFirst().staffId());

        assertNull(slots.getFirst().locationId());

        assertEquals(resource.getId(), slots.getFirst().resourceId());

        when(offering.getStaff()).thenReturn(Set.of(staff));

        when(offering.getStaffRequirement()).thenReturn(REQUIRED);

        when(offering.getResourceRequirement()).thenReturn(FORBIDDEN);

        assertEquals(9, find(null, null, null).size());
    }

    @Test
    void validatesDateRangeAndExcludesPastStarts() {
        assertThrows(ResponseStatusException.class, () ->
            service.findAvailability(
                serviceId,
                date,
                date.minusDays(1),
                null,
                null,
                null
            )
        );

        assertThrows(ResponseStatusException.class, () ->
            service.findAvailability(
                serviceId,
                date,
                date.plusDays(32),
                null,
                null,
                null
            )
        );

        LocalDate past = date.minusWeeks(2);

        assertTrue(
            service
                .findAvailability(serviceId, past, past, null, null, null)
                .isEmpty()
        );
    }

    @Test
    void endpointBindsAssignmentFiltersAndReturnsBookingIdentifiers()
        throws Exception {
        AvailabilityService apiService = mock(AvailabilityService.class);

        var response = new AvailabilitySlotResponse(
            staff.getId(),
            location.getId(),
            resource.getId(),
            at(10, 0),
            at(11, 0)
        );

        when(
            apiService.findAvailability(
                serviceId,
                date,
                date,
                staff.getId(),
                location.getId(),
                resource.getId()
            )
        ).thenReturn(List.of(response));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new AvailabilityController(apiService)
        ).build();

        mvc.perform(
            get("/api/v1/availability")
                .param("serviceId", serviceId.toString())
                .param("from", date.toString())
                .param("to", date.toString())
                .param("staffId", staff.getId().toString())
                .param("locationId", location.getId().toString())
                .param("resourceId", resource.getId().toString())
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].staffId").value(staff.getId().toString()))
            .andExpect(
                jsonPath("$[0].locationId").value(location.getId().toString())
            )
            .andExpect(
                jsonPath("$[0].resourceId").value(resource.getId().toString())
            );

        mvc.perform(
            get("/api/v1/availability")
                .param("serviceId", serviceId.toString())
                .param("from", date.toString())
                .param("to", date.toString())
        ).andExpect(status().isOk());

        verify(apiService).findAvailability(
            serviceId,
            date,
            date,
            null,
            null,
            null
        );
    }

    private List<AvailabilitySlotResponse> find(
        UUID staffId,
        UUID locationId,
        UUID resourceId
    ) {
        return service.findAvailability(
            serviceId,
            date,
            date,
            staffId,
            locationId,
            resourceId
        );
    }

    @Test
    void todaysTeamRequiresWorkingHoursAndFullDayBlocksRemoveStaff() {
        UUID id = staff.getId();

        assertFalse(service.isStaffScheduledToday(id, date, ZoneOffset.UTC));

        when(
            rules.findAllByStaff_IdAndDayOfWeekAndActiveTrue(
                id,
                date.getDayOfWeek()
            )
        ).thenReturn(
            List.of(
                new AvailabilityRule(
                    null,
                    staff,
                    null,
                    date.getDayOfWeek(),
                    LocalTime.of(9, 0),
                    LocalTime.of(17, 0)
                )
            )
        );

        assertTrue(service.isStaffScheduledToday(id, date, ZoneOffset.UTC));

        when(exceptions.findStaffExceptions(eq(id), any(), any())).thenReturn(
            List.of(
                new AvailabilityException(
                    null,
                    staff,
                    null,
                    at(0, 0),
                    at(0, 0).plusDays(1),
                    false
                )
            )
        );

        assertFalse(service.isStaffScheduledToday(id, date, ZoneOffset.UTC));
    }

    @Test
    void todaysTeamIncludesExtraShiftsAndPartiallyBlockedWorkingDays() {
        UUID id = staff.getId();

        when(exceptions.findStaffExceptions(eq(id), any(), any())).thenReturn(
            List.of(
                new AvailabilityException(
                    null,
                    staff,
                    null,
                    at(9, 0),
                    at(17, 0),
                    true
                ),
                new AvailabilityException(
                    null,
                    staff,
                    null,
                    at(9, 0),
                    at(12, 0),
                    false
                )
            )
        );

        assertTrue(service.isStaffScheduledToday(id, date, ZoneOffset.UTC));

        when(exceptions.findStaffExceptions(eq(id), any(), any())).thenReturn(
            List.of(
                new AvailabilityException(
                    null,
                    staff,
                    null,
                    at(9, 0),
                    at(17, 0),
                    true
                ),
                new AvailabilityException(
                    null,
                    staff,
                    null,
                    at(9, 0),
                    at(12, 0),
                    false
                ),
                new AvailabilityException(
                    null,
                    staff,
                    null,
                    at(12, 0),
                    at(17, 0),
                    false
                )
            )
        );

        assertFalse(service.isStaffScheduledToday(id, date, ZoneOffset.UTC));
    }

    private OffsetDateTime at(int hour, int minute) {
        return date.atTime(hour, minute).atOffset(ZoneOffset.UTC);
    }

    private static StaffMember staff() {
        StaffMember staff = mock(StaffMember.class);

        when(staff.getId()).thenReturn(UUID.randomUUID());

        when(staff.isActive()).thenReturn(true);

        when(staff.isFreeAgent()).thenReturn(true);

        return staff;
    }

    private static Location location() {
        Location location = mock(Location.class);

        when(location.getId()).thenReturn(UUID.randomUUID());

        when(location.isActive()).thenReturn(true);

        return location;
    }
}
