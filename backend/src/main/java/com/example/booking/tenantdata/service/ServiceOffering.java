package com.example.booking.tenantdata.service;

import com.example.booking.tenantdata.resource.BookableResource;

import com.example.booking.tenantdata.staff.StaffMember;
import com.example.booking.tenantdata.location.Location;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "services")
public class ServiceOffering {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    private BigDecimal price;

    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_requirement", nullable = false)
    private AssignmentRequirement staffRequirement =
        AssignmentRequirement.FORBIDDEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_requirement", nullable = false)
    private AssignmentRequirement locationRequirement =
        AssignmentRequirement.FORBIDDEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_requirement", nullable = false)
    private AssignmentRequirement resourceRequirement =
        AssignmentRequirement.REQUIRED;

    public AssignmentRequirement getStaffRequirement() {
        return staffRequirement;
    }

    public AssignmentRequirement getLocationRequirement() {
        return locationRequirement;
    }

    public AssignmentRequirement getResourceRequirement() {
        return resourceRequirement;
    }

    public void configureRequirements(
        AssignmentRequirement staff,
        AssignmentRequirement location,
        AssignmentRequirement resource
    ) {
        this.staffRequirement = staff;

        this.locationRequirement = location;

        this.resourceRequirement = resource;
    }

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ManyToMany
    @JoinTable(
        name = "service_staff",
        joinColumns = @JoinColumn(name = "service_id"),
        inverseJoinColumns = @JoinColumn(name = "staff_id")
    )
    private Set<StaffMember> staff = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "service_locations",
        joinColumns = @JoinColumn(name = "service_id"),
        inverseJoinColumns = @JoinColumn(name = "location_id")
    )
    private Set<Location> locations = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "service_resources",
        joinColumns = @JoinColumn(name = "service_id"),
        inverseJoinColumns = @JoinColumn(name = "resource_id")
    )
    private Set<BookableResource> resources = new HashSet<>();

    protected ServiceOffering() {}

    public ServiceOffering(
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency
    ) {
        this.id = UUID.randomUUID();

        this.name = name;

        this.description = description;

        this.durationMinutes = durationMinutes;

        this.price = price;

        this.currency = currency;

        this.active = true;

        this.createdAt = OffsetDateTime.now();
    }

    public void addResource(BookableResource resource) {
        resources.add(resource);
    }

    public void removeResource(BookableResource resource) {
        resources.remove(resource);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Set<BookableResource> getResources() {
        return resources;
    }

    public Set<StaffMember> getStaff() {
        return staff;
    }

    public Set<Location> getLocations() {
        return locations;
    }

    public void replaceStaff(Set<StaffMember> staff) {
        this.staff.clear();

        this.staff.addAll(staff);
    }

    public void replaceLocations(Set<Location> locations) {
        this.locations.clear();

        this.locations.addAll(locations);
    }

    public void update(
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency,
        boolean active
    ) {
        this.name = name;

        this.description = description;

        this.durationMinutes = durationMinutes;

        this.price = price;

        this.currency = currency;

        this.active = active;
    }

    public void replaceResources(Set<BookableResource> resources) {
        this.resources.clear();

        this.resources.addAll(resources);
    }
}
