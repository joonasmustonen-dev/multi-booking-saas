package com.example.booking.tenantdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String email;

    private String phone;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Customer() {
    }

    public Customer(
            String firstName,
            String lastName,
            String email,
            String phone) {

        this.id = UUID.randomUUID();
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}