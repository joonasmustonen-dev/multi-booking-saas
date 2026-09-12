package com.example.booking.tenantdata.availability;

import com.example.booking.tenantdata.resource.BookableResource;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "availability_exceptions")
public class AvailabilityException {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private BookableResource resource;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(nullable = false)
    private boolean available;

    protected AvailabilityException() {
    }

    public AvailabilityException(
            BookableResource resource,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean available) {

        this.id = UUID.randomUUID();
        this.resource = resource;
        this.startAt = startAt;
        this.endAt = endAt;
        this.available = available;
    }

    public UUID getId() { return id; }
    public BookableResource getResource() { return resource; }
    public OffsetDateTime getStartAt() { return startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public boolean isAvailable() { return available; }
}