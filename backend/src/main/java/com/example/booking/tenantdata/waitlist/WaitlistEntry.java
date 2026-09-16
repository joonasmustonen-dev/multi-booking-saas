package com.example.booking.tenantdata.waitlist;

import com.example.booking.tenantdata.Customer;
import com.example.booking.tenantdata.location.Location;
import com.example.booking.tenantdata.service.ServiceOffering;
import com.example.booking.tenantdata.staff.StaffMember;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_staff_id")
    private StaffMember preferredStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_location_id")
    private Location preferredLocation;

    @Column(name = "window_start", nullable = false)
    private OffsetDateTime windowStart;

    @Column(name = "window_end", nullable = false)
    private OffsetDateTime windowEnd;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitlistStatus status;

    @Column(name = "notification_consent", nullable = false)
    private boolean notificationConsent;

    @Column(name = "consent_recorded_at")
    private OffsetDateTime consentRecordedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WaitlistEntry() {}

    public WaitlistEntry(
        Customer customer,
        ServiceOffering service,
        StaffMember preferredStaff,
        Location preferredLocation,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        OffsetDateTime expiresAt,
        boolean notificationConsent
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        this.id = UUID.randomUUID();
        this.customer = customer;
        this.service = service;
        this.preferredStaff = preferredStaff;
        this.preferredLocation = preferredLocation;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.expiresAt = expiresAt;
        this.status = WaitlistStatus.WAITING;
        this.notificationConsent = notificationConsent;
        this.consentRecordedAt = notificationConsent ? now : null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void offer() {
        require(WaitlistStatus.WAITING);
        status = WaitlistStatus.OFFERED;
        updatedAt = OffsetDateTime.now();
    }

    public void accept() {
        require(WaitlistStatus.OFFERED);
        status = WaitlistStatus.ACCEPTED;
        updatedAt = OffsetDateTime.now();
    }

    public void expire() {
        if (status != WaitlistStatus.WAITING && status != WaitlistStatus.OFFERED)
            throw new IllegalStateException("Waitlist entry cannot expire from " + status);
        status = WaitlistStatus.EXPIRED;
        updatedAt = OffsetDateTime.now();
    }

    public void remove() {
        if (status == WaitlistStatus.ACCEPTED || status == WaitlistStatus.REMOVED)
            throw new IllegalStateException("Waitlist entry cannot be removed from " + status);
        status = WaitlistStatus.REMOVED;
        updatedAt = OffsetDateTime.now();
    }

    private void require(WaitlistStatus expected) {
        if (status != expected)
            throw new IllegalStateException("Expected " + expected + " but was " + status);
    }

    public UUID getId() { return id; }
    public Customer getCustomer() { return customer; }
    public ServiceOffering getService() { return service; }
    public StaffMember getPreferredStaff() { return preferredStaff; }
    public Location getPreferredLocation() { return preferredLocation; }
    public OffsetDateTime getWindowStart() { return windowStart; }
    public OffsetDateTime getWindowEnd() { return windowEnd; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public WaitlistStatus getStatus() { return status; }
    public boolean isNotificationConsent() { return notificationConsent; }
    public OffsetDateTime getConsentRecordedAt() { return consentRecordedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
