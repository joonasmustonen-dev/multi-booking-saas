package com.example.booking.tenantdata.appointment;

import com.example.booking.tenant.TenantContext;

import com.example.booking.tenantdata.Customer;
import com.example.booking.tenantdata.CustomerRepository;

import com.example.booking.tenantdata.availability.AvailabilityException;
import com.example.booking.tenantdata.availability.AvailabilityExceptionRepository;
import com.example.booking.tenantdata.availability.AvailabilityRule;
import com.example.booking.tenantdata.availability.AvailabilityRuleRepository;

import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.resource.BookableResourceRepository;
import com.example.booking.tenantdata.resource.ResourceType;

import com.example.booking.tenantdata.service.ServiceOffering;
import com.example.booking.tenantdata.service.ServiceOfferingRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        properties = "app.security.jwt-enabled=false"
)
class AppointmentSafetyIntegrationTest {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BookableResourceRepository resourceRepository;

    @Autowired
    private ServiceOfferingRepository serviceRepository;

    @Autowired
    private AvailabilityRuleRepository ruleRepository;

    @Autowired
    private AvailabilityExceptionRepository exceptionRepository;

    private final List<UUID> appointmentIds =
            new ArrayList<>();

    private final List<UUID> exceptionIds =
            new ArrayList<>();

    private final List<UUID> ruleIds =
            new ArrayList<>();

    private final List<UUID> serviceIds =
            new ArrayList<>();

    private final List<UUID> resourceIds =
            new ArrayList<>();

    private final List<UUID> customerIds =
            new ArrayList<>();

    @BeforeEach
    void setupTenant() {

        TenantContext.setTenantId("tenant-a");
    }

    @AfterEach
    void cleanup() {

        TenantContext.setTenantId("tenant-a");

        for (UUID id : appointmentIds) {
            appointmentRepository
                    .findById(id)
                    .ifPresent(
                            appointmentRepository::delete
                    );
        }

        appointmentRepository.flush();

        for (UUID id : exceptionIds) {
            exceptionRepository
                    .findById(id)
                    .ifPresent(
                            exceptionRepository::delete
                    );
        }

        for (UUID id : ruleIds) {
            ruleRepository
                    .findById(id)
                    .ifPresent(
                            ruleRepository::delete
                    );
        }

        for (UUID id : serviceIds) {
            serviceRepository
                    .findById(id)
                    .ifPresent(
                            serviceRepository::delete
                    );
        }

        for (UUID id : resourceIds) {
            resourceRepository
                    .findById(id)
                    .ifPresent(
                            resourceRepository::delete
                    );
        }

        for (UUID id : customerIds) {
            customerRepository
                    .findById(id)
                    .ifPresent(
                            customerRepository::delete
                    );
        }

        TenantContext.clear();
    }

    /*
     * This directly tests PostgreSQL's exclusion constraint,
     * rather than relying on AppointmentService's pre-check.
     */
    @Test
    void databaseRejectsOverlappingAppointments() {

        LocalDate date =
                futureDate();

        Fixture fixture =
                createFixture(date);

        OffsetDateTime firstStart =
                at(date, 10, 0);

        Appointment first =
                new Appointment(
                        fixture.customer(),
                        fixture.service(),
                        fixture.resource(),
                        firstStart,
                        firstStart.plusHours(1),
                        "First appointment"
                );

        appointmentRepository.saveAndFlush(first);

        appointmentIds.add(first.getId());

        OffsetDateTime overlappingStart =
                at(date, 10, 30);

        Appointment overlapping =
                new Appointment(
                        fixture.customer(),
                        fixture.service(),
                        fixture.resource(),
                        overlappingStart,
                        overlappingStart.plusHours(1),
                        "Should fail"
                );

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        appointmentRepository
                                .saveAndFlush(overlapping)
        );
    }

    /*
     * Proves that POST /appointments cannot bypass
     * the availability rules.
     */
    @Test
    void appointmentCreationRespectsAvailabilityRules() {

        LocalDate date =
                futureDate();

        Fixture fixture =
                createFixture(date);

        AvailabilityException blocked =
                new AvailabilityException(
                        fixture.resource(),
                        at(date, 12, 0),
                        at(date, 13, 0),
                        false
                );

        exceptionRepository.saveAndFlush(blocked);

        exceptionIds.add(blocked.getId());

        /*
         * 10:00 is inside 09:00-17:00.
         */
        AppointmentResponse valid =
                appointmentService.create(
                        new CreateAppointmentRequest(
                                fixture.customer().getId(),
                                fixture.service().getId(),
                                fixture.resource().getId(),
                                at(date, 10, 0),
                                null
                        )
                );

        appointmentIds.add(valid.id());

        assertEquals(
                at(date, 10, 0),
                valid.startAt()
        );

        /*
         * 12:00 is blocked by an exception.
         */
        ResponseStatusException blockedException =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                appointmentService.create(
                                        new CreateAppointmentRequest(
                                                fixture.customer().getId(),
                                                fixture.service().getId(),
                                                fixture.resource().getId(),
                                                at(date, 12, 0),
                                                null
                                        )
                                )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                blockedException.getStatusCode()
        );

        /*
         * 18:00 is outside 09:00-17:00.
         */
        ResponseStatusException outsideHours =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                appointmentService.create(
                                        new CreateAppointmentRequest(
                                                fixture.customer().getId(),
                                                fixture.service().getId(),
                                                fixture.resource().getId(),
                                                at(date, 18, 0),
                                                null
                                        )
                                )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                outsideHours.getStatusCode()
        );
    }

    /*
     * Proves rescheduling goes through exactly
     * the same availability rules.
     */
    @Test
    void rescheduleCannotMoveOntoExistingAppointment() {

        LocalDate date =
                futureDate();

        Fixture fixture =
                createFixture(date);

        AppointmentResponse first =
                appointmentService.create(
                        new CreateAppointmentRequest(
                                fixture.customer().getId(),
                                fixture.service().getId(),
                                fixture.resource().getId(),
                                at(date, 10, 0),
                                "First"
                        )
                );

        appointmentIds.add(first.id());

        AppointmentResponse second =
                appointmentService.create(
                        new CreateAppointmentRequest(
                                fixture.customer().getId(),
                                fixture.service().getId(),
                                fixture.resource().getId(),
                                at(date, 12, 0),
                                "Second"
                        )
                );

        appointmentIds.add(second.id());

        /*
         * Try moving appointment 1 onto appointment 2.
         */
        ResponseStatusException conflict =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                appointmentService.reschedule(
                                        first.id(),
                                        new RescheduleAppointmentRequest(
                                                fixture.resource().getId(),
                                                at(date, 12, 0)
                                        )
                                )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                conflict.getStatusCode()
        );

        /*
         * 14:00 is actually free.
         */
        AppointmentResponse rescheduled =
                appointmentService.reschedule(
                        first.id(),
                        new RescheduleAppointmentRequest(
                                fixture.resource().getId(),
                                at(date, 14, 0)
                        )
                );

        assertEquals(
                at(date, 14, 0),
                rescheduled.startAt()
        );

        assertEquals(
                at(date, 15, 0),
                rescheduled.endAt()
        );
    }

    private Fixture createFixture(
            LocalDate date) {

        Customer customer =
                customerRepository.saveAndFlush(
                        new Customer(
                                "Integration",
                                "Customer",
                                "integration-"
                                        + UUID.randomUUID()
                                        + "@test.local",
                                null
                        )
                );

        customerIds.add(customer.getId());

        BookableResource resource =
                resourceRepository.saveAndFlush(
                        new BookableResource(
                                "Integration Resource",
                                ResourceType.STAFF
                        )
                );

        resourceIds.add(resource.getId());

        ServiceOffering service =
                new ServiceOffering(
                        "Integration Service",
                        "Integration test service",
                        60,
                        new BigDecimal("50.00"),
                        "EUR"
                );

        service.replaceResources(
                Set.of(resource)
        );

        serviceRepository.saveAndFlush(service);

        serviceIds.add(service.getId());

        AvailabilityRule rule =
                new AvailabilityRule(
                        resource,
                        date.getDayOfWeek(),
                        LocalTime.of(9, 0),
                        LocalTime.of(17, 0)
                );

        ruleRepository.saveAndFlush(rule);

        ruleIds.add(rule.getId());

        return new Fixture(
                customer,
                resource,
                service
        );
    }

    private LocalDate futureDate() {

        return LocalDate
                .now(ZoneOffset.UTC)
                .plusDays(7);
    }

    private OffsetDateTime at(
            LocalDate date,
            int hour,
            int minute) {

        return date
                .atTime(hour, minute)
                .atOffset(ZoneOffset.UTC);
    }

    private record Fixture(
            Customer customer,
            BookableResource resource,
            ServiceOffering service) {
    }
}