package com.example.booking.tenantdata;

import com.example.booking.tenant.TenantContext;
import com.example.booking.tenantdata.customer.*;
import com.example.booking.tenantdata.appointment.*;
import com.example.booking.tenantdata.staff.*;
import com.example.booking.tenantdata.service.*;
import com.example.booking.tenantdata.management.CreateAssignmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest(properties = "app.security.jwt-enabled=false")
@AutoConfigureMockMvc
@WithMockUser(roles = "TENANT_ADMIN")
class CustomerOperationsIntegrationTest {
    @Autowired @Qualifier("tenantTransactionManager") PlatformTransactionManager manager;
    @Autowired CustomerRepository customers;
    @Autowired CustomerService customerService;
    @Autowired CustomerOperationsService operations;
    @Autowired StaffMemberService staffService;
    @Autowired StaffMemberRepository staff;
    @Autowired ServiceOfferingService services;
    @Autowired ServiceOfferingRepository offerings;
    @Autowired AppointmentRepository appointments;
    @Autowired MockMvc mvc;

    @Test void searchHandlesCaseFormattedPhonesLiteralWildcardsAndLimits() {
        inTenant(() -> {
            String token = "Lookup" + UUID.randomUUID().toString().replace("-", "");
            var first = customers.saveAndFlush(new Customer(token, "Alpha", token + "@example.com", "+358 (40) 123-9876"));
            customers.saveAndFlush(new Customer(token, "Beta", null, null));
            assertEquals(2, operations.search(token.toLowerCase(), 25).size());
            assertEquals(first.getId(), operations.search("040 123", 25).getFirst().id());
            assertEquals(first.getId(), operations.search(token + "@example.com", 25).getFirst().id());
            assertEquals(1, operations.search(token, 1).size());
            assertFalse(operations.search("%" + token, 25).stream().anyMatch(c -> c.id().equals(first.getId())));
            bad(() -> operations.search(token, 51));
            bad(() -> operations.search("x".repeat(101), 25));
        });
    }

    @Test void preferredStaffPersistsClearsAndRejectsUnavailableAssignments() {
        inTenant(() -> {
            UUID staffId = staffService.create(new CreateAssignmentRequest("Preferred Sofia")).id();
            var customer = customerService.create(new CreateCustomerRequest("Preference", "Customer", null, null, staffId));
            assertEquals(staffId, customer.getPreferredStaffId());
            assertEquals("Preferred Sofia", operations.activity(customer.getId(), 0, 20).preferredStaffName());
            bad(() -> customerService.create(new CreateCustomerRequest("Bad", "Preference", null, null, UUID.randomUUID())));
            staffService.setActive(staffId, false);
            bad(() -> customerService.create(new CreateCustomerRequest("Inactive", "Preference", null, null, staffId)));
            customerService.update(customer.getId(), new UpdateCustomerRequest("Preference", "Customer", null, null, staffId));
            customerService.update(customer.getId(), new UpdateCustomerRequest("Preference", "Customer", null, null, null));
            assertNull(customer.getPreferredStaffId());
        });
    }

    @Test void historyReportsFactualStatusesAndOnlyCompletedPastVisitsAsUsualServices() {
        inTenant(() -> {
            UUID staffId = staffService.create(new CreateAssignmentRequest("History Sofia")).id();
            var customer = customers.saveAndFlush(new Customer("History", "Customer", null, null));
            UUID serviceId = services.create(new CreateServiceRequest("Usual haircut", null, 60, null, "EUR",
                    Set.of(staffId), Set.of(), Set.of(), AssignmentRequirement.REQUIRED,
                    AssignmentRequirement.FORBIDDEN, AssignmentRequirement.FORBIDDEN)).id();
            var service = offerings.findById(serviceId).orElseThrow();
            OffsetDateTime now = OffsetDateTime.now().withNano(0);
            OffsetDateTime lastVisit = null;
            AppointmentStatus[] statuses = {AppointmentStatus.COMPLETED, AppointmentStatus.COMPLETED,
                    AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW, AppointmentStatus.CONFIRMED};
            for (int i = 0; i < statuses.length; i++) {
                OffsetDateTime start = now.minusDays(10 - i);
                var booking = new Appointment(customer, service, staff.findById(staffId).orElseThrow(), null, null,
                        start, start.plusHours(1), "Do not expose appointment notes in customer history");
                if (statuses[i] != AppointmentStatus.CONFIRMED) booking.changeStatus(statuses[i]);
                appointments.saveAndFlush(booking);
                if (statuses[i] == AppointmentStatus.COMPLETED) lastVisit = booking.getEndAt();
            }
            var result = operations.activity(customer.getId(), 0, 2);
            assertEquals(5, result.totalBookings()); assertEquals(2, result.completedBookings());
            assertEquals(1, result.cancelledBookings()); assertEquals(1, result.noShows());
            assertEquals(lastVisit.toInstant(), result.lastVisit().toInstant());
            assertEquals(1, result.mostBookedServices().size()); assertEquals(2, result.mostBookedServices().getFirst().visits());
            assertEquals(2, result.bookings().size()); assertEquals(3, result.totalPages());
            assertEquals(AppointmentStatus.CONFIRMED, result.bookings().getFirst().status());
            assertEquals(1, operations.activity(customer.getId(), 2, 2).bookings().size());
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> customerService.delete(customer.getId())).getStatusCode());
            mvc.perform(get("/api/v1/customers/" + customer.getId() + "/activity"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.noShows").value(1))
                    .andExpect(jsonPath("$.bookings[0].notes").doesNotExist());
        });
    }

    @Test void customerOperationsStayInTenantAndRequireStaffRoles() {
        inTenant(() -> {
            String token = "Private" + UUID.randomUUID();
            var customer = customers.saveAndFlush(new Customer(token, "Customer", null, null));
            assertEquals(1, operations.search(token, 25).size());
            TenantContext.setTenantId("tenant-b");
            try {
                var isolated = new TransactionTemplate(manager);
                isolated.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                isolated.executeWithoutResult(tx -> {
                    tx.setRollbackOnly();
                    assertTrue(operations.search(token, 25).isEmpty());
                    assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                            () -> operations.activity(customer.getId(), 0, 20)).getStatusCode());
                });
            } finally { TenantContext.setTenantId("tenant-a"); }
            mvc.perform(get("/api/v1/customers/search").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/customers/" + customer.getId() + "/activity")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                    .andExpect(status().isForbidden());
        });
    }

    @Test void emptyCustomerAndInvalidHistoryParametersAreHandled() {
        inTenant(() -> {
            var customer = customers.saveAndFlush(new Customer("New", "Customer", null, null));
            var result = operations.activity(customer.getId(), 0, 20);
            assertEquals(0, result.totalBookings()); assertNull(result.lastVisit());
            assertTrue(result.mostBookedServices().isEmpty()); assertTrue(result.bookings().isEmpty());
            bad(() -> operations.activity(customer.getId(), -1, 20));
            bad(() -> operations.activity(customer.getId(), 0, 51));
            customerService.delete(customer.getId());
            assertFalse(customers.existsById(customer.getId()));
        });
    }
    private void bad(Runnable action) {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }
    private void inTenant(Checked action) {
        TenantContext.setTenantId("tenant-a");
        try { new TransactionTemplate(manager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            try { action.run(); } catch (RuntimeException | Error e) { throw e; }
            catch (Exception e) { throw new RuntimeException(e); }
        }); } finally { TenantContext.clear(); }
    }
    private interface Checked { void run() throws Exception; }
}
