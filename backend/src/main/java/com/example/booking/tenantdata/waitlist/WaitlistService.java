package com.example.booking.tenantdata.waitlist;

import com.example.booking.tenantdata.Customer;
import com.example.booking.tenantdata.CustomerRepository;
import com.example.booking.tenantdata.appointment.*;
import com.example.booking.tenantdata.availability.AvailabilityService;
import com.example.booking.tenantdata.location.*;
import com.example.booking.tenantdata.privacy.SecurityAuditService;
import com.example.booking.tenantdata.resource.*;
import com.example.booking.tenantdata.service.*;
import com.example.booking.tenantdata.staff.*;
import com.example.booking.tenantdata.settings.TenantSettingsService;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WaitlistService {
    private final WaitlistEntryRepository entries;
    private final WaitlistOfferRepository offers;
    private final CustomerRepository customers;
    private final ServiceOfferingRepository services;
    private final StaffMemberRepository staff;
    private final LocationRepository locations;
    private final BookableResourceRepository resources;
    private final AppointmentRepository appointments;
    private final AppointmentService appointmentService;
    private final AvailabilityService availability;
    private final SecurityAuditService audit;
    private final TenantSettingsService settings;
    private final ApplicationEventPublisher events;

    public WaitlistService(
        WaitlistEntryRepository entries,
        WaitlistOfferRepository offers,
        CustomerRepository customers,
        ServiceOfferingRepository services,
        StaffMemberRepository staff,
        LocationRepository locations,
        BookableResourceRepository resources,
        AppointmentRepository appointments,
        AppointmentService appointmentService,
        AvailabilityService availability,
        SecurityAuditService audit,
        TenantSettingsService settings,
        ApplicationEventPublisher events
    ) {
        this.entries = entries;
        this.offers = offers;
        this.customers = customers;
        this.services = services;
        this.staff = staff;
        this.locations = locations;
        this.resources = resources;
        this.appointments = appointments;
        this.appointmentService = appointmentService;
        this.availability = availability;
        this.audit = audit;
        this.settings = settings;
        this.events = events;
    }

    @Transactional("tenantTransactionManager")
    public WaitlistEntryResponse create(CreateWaitlistEntryRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowStart = toTenantOffset(request.windowStart());
        OffsetDateTime windowEnd = toTenantOffset(request.windowEnd());
        if (!windowStart.isAfter(now) ||
            request.windowEnd().isAfter(request.windowStart().plusDays(31))) {
            throw badRequest("Waitlist range must be in the future and no longer than 31 days");
        }
        OffsetDateTime expiresAt = request.expiresAt() == null
            ? windowEnd
            : toTenantOffset(request.expiresAt());
        if (!expiresAt.isAfter(now) || expiresAt.isAfter(windowEnd)) {
            throw badRequest("Waitlist expiry must be in the future and within the requested range");
        }

        Customer customer = customers.findById(request.customerId())
            .orElseThrow(() -> notFound("Customer"));
        if (customer.isProcessingRestricted() || customer.getErasedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Waitlist entries cannot be created for this customer");
        }
        if (request.notificationConsent() && blank(customer.getEmail()) && blank(customer.getPhone())) {
            throw badRequest("A customer email or phone is required for waitlist notifications");
        }
        ServiceOffering service = services.findByIdWithResources(request.serviceId())
            .orElseThrow(() -> notFound("Service"));
        if (!service.isActive()) {
            throw conflict("Inactive services cannot accept waitlist requests");
        }
        StaffMember preferredStaff = resolveStaff(request.preferredStaffId());
        Location preferredLocation = resolveLocation(request.preferredLocationId());
        if (preferredStaff != null && !preferredStaff.isActive()) {
            throw conflict("Preferred staff member is inactive");
        }
        if (preferredLocation != null && !preferredLocation.isActive()) {
            throw conflict("Preferred location is inactive");
        }
        if (preferredStaff != null && !service.getStaff().contains(preferredStaff)) {
            throw badRequest("Preferred staff member is not eligible for this service");
        }
        if (preferredLocation != null && !service.getLocations().contains(preferredLocation)) {
            throw badRequest("Preferred location is not eligible for this service");
        }
        if (entries.countActiveOverlaps(customer.getId(), service.getId(),
                windowStart, windowEnd) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "An active waitlist entry already covers this service and time range");
        }

        WaitlistEntry entry = entries.saveAndFlush(new WaitlistEntry(
            customer, service, preferredStaff, preferredLocation,
            windowStart, windowEnd, expiresAt,
            request.notificationConsent()
        ));
        record("WAITLIST_CREATED", entry.getId(), 201);
        return WaitlistEntryResponse.from(entry, null);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<WaitlistEntryResponse> find(UUID customerId, WaitlistStatus status) {
        List<WaitlistEntry> found = customerId == null
            ? entries.findAllWithDetails(org.springframework.data.domain.PageRequest.of(0, 500))
            : entries.findByCustomer_IdOrderByCreatedAtDesc(
                customerId,
                org.springframework.data.domain.PageRequest.of(0, 500)
            );
        return found.stream().filter(e -> status == null || e.getStatus() == status)
            .map(e -> WaitlistEntryResponse.from(e, offers.findByEntry_Id(e.getId()).orElse(null)))
            .toList();
    }

    @Transactional("tenantTransactionManager")
    public WaitlistEntryResponse remove(UUID id) {
        WaitlistEntry entry = locked(id);
        offers.findByEntryForUpdate(id).ifPresent(offer -> {
            if (offer.getStatus() == WaitlistStatus.OFFERED) {
                offer.remove();
                republishSlot(entry, offer);
            }
        });
        try { entry.remove(); } catch (IllegalStateException e) { throw conflict(e.getMessage()); }
        entries.flush();
        record("WAITLIST_REMOVED", id, 200);
        return response(entry);
    }

    @Transactional("tenantTransactionManager")
    public WaitlistEntryResponse expire(UUID id) {
        WaitlistEntry entry = locked(id);
        offers.findByEntryForUpdate(id).ifPresent(offer -> {
            if (offer.getStatus() == WaitlistStatus.OFFERED) {
                offer.expire();
                republishSlot(entry, offer);
            }
        });
        try { entry.expire(); } catch (IllegalStateException e) { throw conflict(e.getMessage()); }
        entries.flush();
        record("WAITLIST_EXPIRED", id, 200);
        return response(entry);
    }

    @Transactional("tenantTransactionManager")
    public WaitlistEntryResponse accept(UUID id) {
        WaitlistEntry entry = locked(id);
        WaitlistOffer offer = offers.findByEntryForUpdate(id)
            .orElseThrow(() -> conflict("This waitlist entry has no active offer"));
        if (entry.getStatus() != WaitlistStatus.OFFERED ||
            offer.getStatus() != WaitlistStatus.OFFERED ||
            !offer.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw conflict("This waitlist offer is no longer available");
        }
        AppointmentResponse created = appointmentService.create(new CreateAppointmentRequest(
            entry.getCustomer().getId(), entry.getService().getId(),
            offer.getStaff() == null ? null : offer.getStaff().getId(),
            offer.getLocation() == null ? null : offer.getLocation().getId(),
            offer.getResource() == null ? null : offer.getResource().getId(),
            offer.getStartAt(), "Created from waitlist offer"
        ));
        Appointment appointment = appointments.findByIdWithDetails(created.id()).orElseThrow();
        offer.accept(appointment);
        entry.accept();
        entries.flush();
        record("WAITLIST_ACCEPTED", id, 200);
        return response(entry);
    }

    @Transactional(
        value = "tenantTransactionManager",
        propagation = Propagation.REQUIRES_NEW
    )
    public Optional<WaitlistEntryResponse> matchCancellation(AppointmentCancelledEvent event) {
        OffsetDateTime now = OffsetDateTime.now();
        if (!event.startAt().isAfter(now)) return Optional.empty();
        ServiceOffering service = services.findByIdWithResources(event.serviceId()).orElse(null);
        if (service == null) return Optional.empty();
        StaffMember assignedStaff = resolveStaff(event.staffId());
        Location assignedLocation = resolveLocation(event.locationId());
        BookableResource resource = event.resourceId() == null ? null
            : resources.findById(event.resourceId()).orElse(null);
        if (!availability.isAvailable(service, assignedStaff, assignedLocation, resource, event.startAt())) {
            return Optional.empty();
        }
        for (WaitlistEntry candidate : entries.findCandidates(
            event.serviceId(), event.staffId(), event.locationId(),
            event.startAt(), event.endAt(), now)) {
            Customer customer = candidate.getCustomer();
            if (customer.isProcessingRestricted() || customer.getErasedAt() != null) continue;
            NotificationChannel channel = candidate.isNotificationConsent()
                ? !blank(customer.getEmail())
                    ? NotificationChannel.EMAIL
                    : NotificationChannel.SMS
                : NotificationChannel.IN_APP;
            OffsetDateTime offerExpiry = min(candidate.getExpiresAt(), event.startAt(), now.plusMinutes(30));
            if (!offerExpiry.isAfter(now)) continue;
            candidate.offer();
            WaitlistOffer offer = new WaitlistOffer(candidate, assignedStaff, assignedLocation,
                resource, event.startAt(), event.endAt(), channel, offerExpiry);
            offers.saveAndFlush(offer);
            entries.flush();
            record("WAITLIST_OFFERED", candidate.getId(), 202);
            return Optional.of(WaitlistEntryResponse.from(candidate, offer));
        }
        return Optional.empty();
    }

    @Transactional("tenantTransactionManager")
    public int expireDue() {
        List<WaitlistEntry> due = entries.findExpired(
            OffsetDateTime.now(), org.springframework.data.domain.PageRequest.of(0, 500)
        );
        for (WaitlistEntry entry : due) {
            offers.findByEntryForUpdate(entry.getId()).ifPresent(offer -> {
                if (offer.getStatus() == WaitlistStatus.OFFERED) {
                    offer.expire();
                    republishSlot(entry, offer);
                }
            });
            entry.expire();
            record("WAITLIST_EXPIRED", entry.getId(), 200);
        }
        entries.flush();
        return due.size();
    }

    private WaitlistEntry locked(UUID id) {
        return entries.findForUpdate(id).orElseThrow(() -> notFound("Waitlist entry"));
    }
    private WaitlistEntryResponse response(WaitlistEntry entry) {
        return WaitlistEntryResponse.from(entry, offers.findByEntry_Id(entry.getId()).orElse(null));
    }
    private StaffMember resolveStaff(UUID id) {
        if (id == null) return null;
        return staff.findById(id).orElseThrow(() -> notFound("Staff member"));
    }
    private Location resolveLocation(UUID id) {
        if (id == null) return null;
        return locations.findById(id).orElseThrow(() -> notFound("Location"));
    }
    private void record(String action, UUID id, int outcome) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        audit.record(auth == null ? "system:waitlist" : auth.getName(), action, id, null, outcome);
    }
    private void republishSlot(WaitlistEntry entry, WaitlistOffer offer) {
        events.publishEvent(new AppointmentCancelledEvent(
            entry.getService().getId(),
            offer.getStaff() == null ? null : offer.getStaff().getId(),
            offer.getLocation() == null ? null : offer.getLocation().getId(),
            offer.getResource() == null ? null : offer.getResource().getId(),
            offer.getStartAt(), offer.getEndAt()
        ));
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private OffsetDateTime toTenantOffset(java.time.LocalDateTime local) {
        var zoned = local.atZone(settings.getZoneId());
        if (!zoned.toLocalDateTime().equals(local)) {
            throw badRequest("The selected local time does not exist in the workspace time zone");
        }
        return zoned.toOffsetDateTime();
    }
    private static OffsetDateTime min(OffsetDateTime... values) {
        return Arrays.stream(values).min(Comparator.naturalOrder()).orElseThrow();
    }
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
    private static ResponseStatusException notFound(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " not found");
    }
}
