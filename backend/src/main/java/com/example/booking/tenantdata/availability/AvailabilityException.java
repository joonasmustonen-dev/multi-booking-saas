package com.example.booking.tenantdata.availability;

import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.staff.StaffMember;
import com.example.booking.tenantdata.location.Location;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "availability_exceptions")
public class AvailabilityException {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id")
    private BookableResource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private StaffMember staff;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "schedule_week")
    private java.time.LocalDate scheduleWeek;
    public java.time.LocalDate getScheduleWeek() { return scheduleWeek; }
    public void setScheduleWeek(java.time.LocalDate week) { this.scheduleWeek = week; }

    protected AvailabilityException() {
    }

    public AvailabilityException(
            BookableResource resource,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean available) {
        this(resource, null, null, startAt, endAt, available);
    }

    public AvailabilityException(
            BookableResource resource,
            StaffMember staff,
            Location location,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean available) {

        this.id = UUID.randomUUID();
        this.resource = resource;
        this.staff = staff;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.available = available;
    }

    public StaffMember getStaff() { return staff; }
    public Location getLocation() { return location; }
    public void update(OffsetDateTime start, OffsetDateTime end, boolean available) {
        this.startAt = start; this.endAt = end; this.available = available;
    }

    public UUID getId() { return id; }
    public BookableResource getResource() { return resource; }
    public StaffMember getStaffResource() { return staff; }
    public Location getLocationResource() { return location; }
    public OffsetDateTime getStartAt() { return startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public boolean isAvailable() { return available; }
}