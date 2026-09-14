package com.example.booking.tenantdata.availability;

import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.staff.StaffMember;
import com.example.booking.tenantdata.location.Location;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "availability_rules")
public class AvailabilityRule {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private boolean active;

    protected AvailabilityRule() {
    }

    public AvailabilityRule(
            BookableResource resource,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime) {
        this(resource, null, null, dayOfWeek, startTime, endTime);
    }

    public AvailabilityRule(
            BookableResource resource,
            StaffMember staff,
            Location location,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime) {

        this.id = UUID.randomUUID();
        this.resource = resource;
        this.staff = staff;
        this.location = location;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.active = true;
    }

    public void update(DayOfWeek day, LocalTime start, LocalTime end, boolean active) {
        this.dayOfWeek = day; this.startTime = start; this.endTime = end; this.active = active;
    }

    public UUID getId() { return id; }
    public StaffMember getStaff() {return staff;}
    public Location getLocation() {return location;}
    public BookableResource getResource() {return resource;}
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public boolean isActive() { return active; }
}