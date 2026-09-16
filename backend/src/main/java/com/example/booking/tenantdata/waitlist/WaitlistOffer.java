package com.example.booking.tenantdata.waitlist;

import com.example.booking.tenantdata.appointment.Appointment;
import com.example.booking.tenantdata.location.Location;
import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.staff.StaffMember;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "waitlist_offers")
public class WaitlistOffer {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false, unique = true)
    private WaitlistEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private StaffMember staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id")
    private BookableResource resource;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitlistStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_channel", nullable = false)
    private NotificationChannel notificationChannel;

    @Column(name = "notification_queued_at", nullable = false)
    private OffsetDateTime notificationQueuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", unique = true)
    private Appointment appointment;

    protected WaitlistOffer() {}

    public WaitlistOffer(
        WaitlistEntry entry,
        StaffMember staff,
        Location location,
        BookableResource resource,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        NotificationChannel notificationChannel,
        OffsetDateTime expiresAt
    ) {
        this.id = UUID.randomUUID();
        this.entry = entry;
        this.staff = staff;
        this.location = location;
        this.resource = resource;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = WaitlistStatus.OFFERED;
        this.notificationChannel = notificationChannel;
        this.notificationQueuedAt = OffsetDateTime.now();
        this.expiresAt = expiresAt;
    }

    public void accept(Appointment appointment) {
        requireOffered();
        status = WaitlistStatus.ACCEPTED;
        this.appointment = appointment;
    }

    public void expire() {
        requireOffered();
        status = WaitlistStatus.EXPIRED;
    }

    public void remove() {
        if (status == WaitlistStatus.OFFERED) status = WaitlistStatus.REMOVED;
    }

    private void requireOffered() {
        if (status != WaitlistStatus.OFFERED)
            throw new IllegalStateException("Offer is no longer active");
    }

    public UUID getId() { return id; }
    public WaitlistEntry getEntry() { return entry; }
    public StaffMember getStaff() { return staff; }
    public Location getLocation() { return location; }
    public BookableResource getResource() { return resource; }
    public OffsetDateTime getStartAt() { return startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public WaitlistStatus getStatus() { return status; }
    public NotificationChannel getNotificationChannel() { return notificationChannel; }
    public OffsetDateTime getNotificationQueuedAt() { return notificationQueuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public Appointment getAppointment() { return appointment; }
}
