package com.example.booking.tenantdata.appointment;

import com.example.booking.tenantdata.Customer;
import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.service.ServiceOffering;
import com.example.booking.tenantdata.location.Location;
import com.example.booking.tenantdata.staff.StaffMember;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment {

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
    @JoinColumn(name = "resource_id")
    private BookableResource resource;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private StaffMember staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Appointment() {}

    public Appointment(
        Customer customer,
        ServiceOffering service,
        StaffMember staff,
        Location location,
        BookableResource resource,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String notes
    ) {
        this.id = UUID.randomUUID();

        this.customer = customer;

        this.service = service;

        this.staff = staff;

        this.location = location;

        this.resource = resource;

        this.startAt = startAt;

        this.endAt = endAt;

        this.status = AppointmentStatus.CONFIRMED;

        this.notes = notes;

        this.createdAt = OffsetDateTime.now();
    }

    public Appointment(
        Customer customer,
        ServiceOffering service,
        StaffMember staff,
        Location location,
        BookableResource resource,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String notes,
        AppointmentStatus initialStatus
    ) {
        this(
            customer,
            service,
            staff,
            location,
            resource,
            startAt,
            endAt,
            notes
        );
        if (
            initialStatus != AppointmentStatus.PENDING &&
            initialStatus != AppointmentStatus.CONFIRMED
        ) throw new IllegalArgumentException("Invalid initial booking status");
        this.status = initialStatus;
    }

    public void changeStatus(AppointmentStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition appointment from " +
                    status +
                    " to " +
                    newStatus
            );
        }

        this.status = newStatus;
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public ServiceOffering getService() {
        return service;
    }

    public BookableResource getResource() {
        return resource;
    }

    public OffsetDateTime getStartAt() {
        return startAt;
    }

    public OffsetDateTime getEndAt() {
        return endAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public StaffMember getStaff() {
        return staff;
    }

    public Location getLocation() {
        return location;
    }

    public void reschedule(
        StaffMember staff,
        Location location,
        BookableResource resource,
        OffsetDateTime startAt,
        OffsetDateTime endAt
    ) {
        this.resource = resource;

        this.startAt = startAt;

        this.endAt = endAt;
    }
}
