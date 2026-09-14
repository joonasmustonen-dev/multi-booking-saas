package com.example.booking.tenantdata.resource;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "resources")
public class BookableResource {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType type;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false, length = 1000)
    private String description = "";
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    protected BookableResource() {
    }

    public BookableResource(
            String name,
            ResourceType type) {

        this.id = UUID.randomUUID();
        this.name = name;
        this.type = type;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
    }

    public void update(
            String name,
            ResourceType type,
            boolean active) {

        this.name = name;
        this.type = type;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ResourceType getType() {
        return type;
    }



    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}