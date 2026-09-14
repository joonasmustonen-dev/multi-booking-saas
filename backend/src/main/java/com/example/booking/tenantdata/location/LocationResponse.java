package com.example.booking.tenantdata.location;

import java.util.UUID;
import java.time.OffsetDateTime;
public record LocationResponse(
    UUID id,
    String name,
    boolean active,
    OffsetDateTime createdAt,
    String description,
    String addressLine,
    String city,
    String postalCode,
    String countryCode,
    String phone
) {
    public static LocationResponse from(Location entity) {
        return new LocationResponse(
            entity.getId(),
            entity.getName(),
            entity.isActive(),
            entity.getCreatedAt(),
            entity.getDescription(),
            entity.getAddressLine(),
            entity.getCity(),
            entity.getPostalCode(),
            entity.getCountryCode(),
            entity.getPhone()
        );
    }
}
