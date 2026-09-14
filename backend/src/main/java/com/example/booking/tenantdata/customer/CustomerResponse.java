package com.example.booking.tenantdata.customer;

import com.example.booking.tenantdata.Customer;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String phone,
    UUID preferredStaffId,
    OffsetDateTime createdAt,
    boolean processingRestricted,
    boolean legalHold,
    OffsetDateTime erasedAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
            customer.getId(),
            customer.getFirstName(),
            customer.getLastName(),
            customer.getEmail(),
            customer.getPhone(),
            customer.getPreferredStaffId(),
            customer.getCreatedAt(),
            customer.isProcessingRestricted(),
            customer.isLegalHold(),
            customer.getErasedAt()
        );
    }
}
