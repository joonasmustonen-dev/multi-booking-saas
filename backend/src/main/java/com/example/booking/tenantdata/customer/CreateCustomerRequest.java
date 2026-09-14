package com.example.booking.tenantdata.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @Email
        @Size(max = 320)
        String email,

        @Size(max = 50)
        String phone,
        java.util.UUID preferredStaffId
) {
    public CreateCustomerRequest(String firstName, String lastName, String email, String phone) {
        this(firstName, lastName, email, phone, null);
    }
}