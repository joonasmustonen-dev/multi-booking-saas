package com.example.booking.tenantdata.location;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            nullable = false,
            length = 150
    )
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(
            name = "created_at",
            nullable = false
    )
    private OffsetDateTime createdAt;


    @Column(name = "description", length = 1000)
    private String description = "";

    @Column(name = "address_line", length = 200)
    private String addressLine = "";

    @Column(name = "city", length = 100)
    private String city = "";

    @Column(name = "postal_code", length = 30)
    private String postalCode = "";

    @Column(name = "country_code", length = 2)
    private String countryCode = "";

    @Column(name = "phone", length = 40)
    private String phone = "";

    protected Location() {
    }


    public Location(
            String name) {

        this.name = name;
        this.active = true;
        this.createdAt =
                OffsetDateTime.now();
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


    public void update(
            String name,
            boolean active) {

        this.name = name;
        this.active = active;
    }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String value) { this.addressLine = value; }
    public String getCity() { return city; }
    public void setCity(String value) { this.city = value; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String value) { this.postalCode = value; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String value) { this.countryCode = value; }
    public String getPhone() { return phone; }
    public void setPhone(String value) { this.phone = value; }
}