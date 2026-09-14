package com.example.booking.tenantdata.staff;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import com.example.booking.tenantdata.location.Location;

@Entity
@Table(name = "staff_members")
public class StaffMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false, length = 254)
    private String email = "";

    @Column(nullable = false, length = 40)
    private String phone = "";

    @Column(name = "free_agent", nullable = false)
    private boolean freeAgent = true;

    @Column(nullable = false)
    private boolean removed = false;

    @ManyToMany
    @JoinTable(
        name = "staff_locations",
        joinColumns = @JoinColumn(name = "staff_id"),
        inverseJoinColumns = @JoinColumn(name = "location_id")
    )
    private Set<Location> locations = new HashSet<>();

    protected StaffMember() {}

    public StaffMember(String name) {
        this.name = name;

        this.active = true;

        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void update(String name, boolean active) {
        this.name = name;

        this.active = active;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isFreeAgent() {
        return freeAgent;
    }

    public boolean isRemoved() {
        return removed;
    }

    public Set<Location> getLocations() {
        return locations;
    }

    public void setContacts(String email, String phone) {
        this.email = email;

        this.phone = phone;
    }

    public void assignLocations(boolean freeAgent, Set<Location> locations) {
        this.freeAgent = freeAgent;

        this.locations.clear();

        this.locations.addAll(locations);
    }

    public void remove() {
        this.removed = true;

        this.active = false;

        this.locations.clear();
    }
}
