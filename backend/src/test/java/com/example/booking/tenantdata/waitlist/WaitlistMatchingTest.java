package com.example.booking.tenantdata.waitlist;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.booking.tenantdata.Customer;
import com.example.booking.tenantdata.CustomerRepository;
import com.example.booking.tenantdata.appointment.*;
import com.example.booking.tenantdata.availability.AvailabilityService;
import com.example.booking.tenantdata.location.LocationRepository;
import com.example.booking.tenantdata.privacy.SecurityAuditService;
import com.example.booking.tenantdata.resource.BookableResourceRepository;
import com.example.booking.tenantdata.service.*;
import com.example.booking.tenantdata.settings.TenantSettingsService;
import com.example.booking.tenantdata.staff.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class WaitlistMatchingTest {

    @Test
    void cancellationMatchingAlwaysStartsAFreshTransactionAfterCommit() throws Exception {
        Transactional transaction = WaitlistService.class
            .getMethod("matchCancellation", AppointmentCancelledEvent.class)
            .getAnnotation(Transactional.class);

        assertNotNull(transaction);
        assertEquals("tenantTransactionManager", transaction.value());
        assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());
    }

    @Test
    void cancellationOffersTheReleasedCombinationToOnlyTheOldestCandidate() {
        WaitlistEntryRepository entries = mock(WaitlistEntryRepository.class);
        WaitlistOfferRepository offers = mock(WaitlistOfferRepository.class);
        ServiceOfferingRepository services = mock(ServiceOfferingRepository.class);
        StaffMemberRepository staff = mock(StaffMemberRepository.class);
        AvailabilityService availability = mock(AvailabilityService.class);
        SecurityAuditService audit = mock(SecurityAuditService.class);
        WaitlistEntry first = candidate("First");
        WaitlistEntry second = candidate("Second");
        ServiceOffering service = first.getService();
        StaffMember assigned = mock(StaffMember.class);
        UUID serviceId = service.getId();
        UUID staffId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        OffsetDateTime end = start.plusHours(1);

        when(assigned.getId()).thenReturn(staffId);
        when(services.findByIdWithResources(serviceId)).thenReturn(Optional.of(service));
        when(staff.findById(staffId)).thenReturn(Optional.of(assigned));
        when(availability.isAvailable(service, assigned, null, null, start)).thenReturn(true);
        when(entries.findCandidates(eq(serviceId), eq(staffId), isNull(),
            eq(start), eq(end), any())).thenReturn(List.of(first, second));

        WaitlistService waitlist = service(entries, offers, services, staff,
            availability, audit);

        var match = waitlist.matchCancellation(new AppointmentCancelledEvent(
            serviceId, staffId, null, null, start, end
        ));

        assertTrue(match.isPresent());
        UUID firstId = first.getId();
        verify(first).offer();
        verify(second, never()).offer();
        verify(offers).saveAndFlush(any(WaitlistOffer.class));
        verify(audit).record(anyString(), eq("WAITLIST_OFFERED"),
            eq(firstId), isNull(), eq(202));
    }

    private WaitlistEntry candidate(String customerName) {
        WaitlistEntry entry = mock(WaitlistEntry.class);
        Customer customer = mock(Customer.class);
        ServiceOffering service = mock(ServiceOffering.class);
        UUID entryId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getCustomer()).thenReturn(customer);
        when(entry.getService()).thenReturn(service);
        when(entry.getWindowStart()).thenReturn(start);
        when(entry.getWindowEnd()).thenReturn(start.plusDays(1));
        when(entry.getExpiresAt()).thenReturn(start.plusDays(1));
        when(entry.getStatus()).thenReturn(WaitlistStatus.OFFERED);
        when(entry.getCreatedAt()).thenReturn(OffsetDateTime.now());
        when(customer.getId()).thenReturn(UUID.randomUUID());
        when(customer.getFirstName()).thenReturn(customerName);
        when(customer.getLastName()).thenReturn("Customer");
        when(customer.getEmail()).thenReturn(customerName.toLowerCase() + "@example.test");
        when(service.getId()).thenReturn(serviceId);
        when(service.getName()).thenReturn("Service");
        return entry;
    }

    private WaitlistService service(
        WaitlistEntryRepository entries,
        WaitlistOfferRepository offers,
        ServiceOfferingRepository services,
        StaffMemberRepository staff,
        AvailabilityService availability,
        SecurityAuditService audit
    ) {
        return new WaitlistService(
            entries,
            offers,
            mock(CustomerRepository.class),
            services,
            staff,
            mock(LocationRepository.class),
            mock(BookableResourceRepository.class),
            mock(AppointmentRepository.class),
            mock(AppointmentService.class),
            availability,
            audit,
            mock(TenantSettingsService.class),
            mock(ApplicationEventPublisher.class)
        );
    }
}
