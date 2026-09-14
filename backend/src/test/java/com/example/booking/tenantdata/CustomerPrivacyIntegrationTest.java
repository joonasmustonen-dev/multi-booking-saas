package com.example.booking.tenantdata;

import com.example.booking.tenant.TenantContext;
import com.example.booking.tenantdata.appointment.*;
import com.example.booking.tenantdata.customer.CustomerOperationsService;
import com.example.booking.tenantdata.privacy.*;
import com.example.booking.tenantdata.service.*;
import com.example.booking.tenantdata.staff.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.security.jwt-enabled=false")
@WithMockUser(roles = "TENANT_ADMIN")
class CustomerPrivacyIntegrationTest {

    @Autowired
    @Qualifier("tenantTransactionManager")
    PlatformTransactionManager manager;

    @Autowired
    CustomerRepository customers;

    @Autowired
    AppointmentRepository appointments;

    @Autowired
    StaffMemberRepository staff;

    @Autowired
    ServiceOfferingRepository services;

    @Autowired
    PrivacyService privacy;

    @Autowired
    CustomerOperationsService operations;

    @Autowired
    CustomerService customerService;

    @PersistenceContext(unitName = "tenant")
    EntityManager entityManager;

    private void inTenant(Runnable action) {
        TenantContext.setTenantId("tenant-a");

        try {
            new TransactionTemplate(manager).executeWithoutResult(
                transaction -> {
                    transaction.setRollbackOnly();

                    action.run();
                }
            );
        } finally {
            TenantContext.clear();
        }
    }

    private Customer customer() {
        return customers.saveAndFlush(
            new Customer(
                "Privacy",
                UUID.randomUUID().toString(),
                "privacy@example.test",
                "+35840123"
            )
        );
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void restrictedCustomersAreHiddenFromStaffAndCalendarNotesAreRedacted() {
        inTenant(() -> {
            Customer customer = customer();

            Appointment appointment = booking(customer, false);

            customer.setPrivacy(true, false);

            customers.flush();

            assertEquals(
                403,
                assertThrows(ResponseStatusException.class, () ->
                    customerService.findById(customer.getId())
                )
                    .getStatusCode()
                    .value()
            );

            assertTrue(operations.search(customer.getLastName(), 25).isEmpty());

            assertNull(AppointmentResponse.from(appointment).notes());

            assertEquals(
                "Restricted customer",
                AppointmentCalendarResponse.from(appointment).customerName()
            );
        });
    }

    private Appointment booking(Customer customer, boolean future) {
        var member = staff.saveAndFlush(
            new StaffMember("Privacy fixture staff")
        );

        var service = services.saveAndFlush(
            new ServiceOffering(
                "Privacy fixture service",
                null,
                60,
                null,
                "EUR"
            )
        );

        var start = OffsetDateTime.now().plusDays(future ? 10 : -100);

        var booking = new Appointment(
            customer,
            service,
            member,
            null,
            null,
            start,
            start.plusHours(1),
            "Private operational notes"
        );

        if (!future) booking.changeStatus(AppointmentStatus.COMPLETED);

        return appointments.saveAndFlush(booking);
    }

    @Test
    void exportsAllHistoryPagesIncludingNotesAndChangesRevisionAfterAnEdit() {
        inTenant(() -> {
            Customer customer = customer();

            booking(customer, false);

            booking(customer, true);

            var first = privacy.export(customer.getId(), 0, 1);

            var second = privacy.export(customer.getId(), 1, 1);

            assertEquals(2, first.totalAppointments());

            assertEquals(2, first.totalPages());

            assertEquals(
                "Private operational notes",
                first.appointments().getFirst().notes()
            );

            assertNotEquals(
                first.appointments().getFirst().id(),
                second.appointments().getFirst().id()
            );

            assertEquals(first.revision(), second.revision());

            customer.update(
                "Changed",
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhone()
            );

            customers.flush();

            assertNotEquals(
                first.revision(),
                privacy.export(customer.getId(), 0, 1).revision()
            );
        });
    }

    @Test
    void legalHoldsPreventBothErasureModesAndRestrictedCustomersLeaveBookingSearch() {
        inTenant(() -> {
            Customer customer = customer();

            booking(customer, true);

            privacy.controls(
                customer.getId(),
                new PrivacyService.Controls(true, true)
            );

            assertTrue(
                operations.search(customer.getLastName(), 25, true).isEmpty()
            );

            assertEquals(
                1,
                operations.search(customer.getLastName(), 25).size()
            );

            for (var mode : PrivacyService.ErasureMode.values()) {
                assertEquals(
                    409,
                    assertThrows(ResponseStatusException.class, () ->
                        privacy.erase(
                            customer.getId(),
                            new PrivacyService.ErasureRequest(mode, true)
                        )
                    )
                        .getStatusCode()
                        .value()
                );
            }
            assertEquals(
                1,
                appointments
                    .findByCustomer_Id(
                        customer.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10)
                    )
                    .getTotalElements()
            );
        });
    }

    @Test
    void contactErasureCancelsCapacityClearsNotesAndCannotBeReopened() {
        inTenant(() -> {
            Customer customer = customer();

            Appointment booking = booking(customer, true);

            UUID bookingId = booking.getId();

            privacy.erase(
                customer.getId(),
                new PrivacyService.ErasureRequest(
                    PrivacyService.ErasureMode.CONTACT_DETAILS,
                    true
                )
            );

            entityManager.clear();

            var erased = customers.findById(customer.getId()).orElseThrow();

            var cancelled = appointments.findById(bookingId).orElseThrow();

            assertNull(erased.getEmail());

            assertNull(erased.getPhone());

            assertNull(erased.getPreferredStaffId());

            assertTrue(erased.isProcessingRestricted());

            assertNotNull(erased.getErasedAt());

            assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus());

            assertNull(cancelled.getNotes());

            assertTrue(
                operations
                    .search("", 50)
                    .stream()
                    .noneMatch(item -> item.id().equals(erased.getId()))
            );

            assertThrows(ResponseStatusException.class, () ->
                privacy.controls(
                    erased.getId(),
                    new PrivacyService.Controls(false, false)
                )
            );
        });
    }

    @Test
    void fullErasureDeletesTheCustomerAndEveryAppointment() {
        inTenant(() -> {
            Customer customer = customer();

            booking(customer, true);

            booking(customer, false);

            privacy.erase(
                customer.getId(),
                new PrivacyService.ErasureRequest(
                    PrivacyService.ErasureMode.ALL_DATA,
                    true
                )
            );

            entityManager.clear();

            assertFalse(customers.existsById(customer.getId()));

            assertFalse(appointments.existsByCustomer_Id(customer.getId()));
        });
    }

    @Test
    void retentionIsDisabledByDefaultAndPreviewTokensExpireAfterPolicyChanges() {
        inTenant(() -> {
            privacy.savePolicy(new PrivacyService.Policy(0, 0, 0, 0));

            var disabled = privacy.preview();

            assertEquals(0, disabled.customers());

            assertEquals(0, disabled.notes());

            assertEquals(0, disabled.staffContacts());

            assertEquals(0, disabled.auditEvents());

            privacy.savePolicy(new PrivacyService.Policy(0, 30, 0, 0));

            assertThrows(ResponseStatusException.class, () ->
                privacy.apply(
                    new PrivacyService.ApplyRequest(disabled.token(), true)
                )
            );
        });
    }

    @Test
    void retentionClearsEligibleNotesButProtectsLegalHoldsAndFutureBookings() {
        inTenant(() -> {
            Customer normal = customer();

            Customer held = customer();

            var old = booking(normal, false);

            var protectedBooking = booking(held, false);

            var future = booking(normal, true);

            privacy.controls(
                held.getId(),
                new PrivacyService.Controls(false, true)
            );

            privacy.savePolicy(new PrivacyService.Policy(0, 30, 0, 0));

            var preview = privacy.preview();

            assertTrue(preview.notes() >= 1);

            privacy.apply(
                new PrivacyService.ApplyRequest(preview.token(), true)
            );

            entityManager.clear();

            assertNull(
                appointments.findById(old.getId()).orElseThrow().getNotes()
            );

            assertNotNull(
                appointments
                    .findById(protectedBooking.getId())
                    .orElseThrow()
                    .getNotes()
            );

            assertNotNull(
                appointments.findById(future.getId()).orElseThrow().getNotes()
            );
        });
    }

    @Test
    void sqlLookingCustomerInputIsMatchedLiterallyAndCannotAlterTables() {
        inTenant(() -> {
            String payload = "O'Connor'); DROP TABLE customers; --";

            var customer = customers.saveAndFlush(
                new Customer(payload, UUID.randomUUID().toString(), null, null)
            );

            assertTrue(
                operations
                    .search(payload, 25)
                    .stream()
                    .anyMatch(item -> item.id().equals(customer.getId()))
            );

            assertTrue(customers.existsById(customer.getId()));
        });
    }
}
