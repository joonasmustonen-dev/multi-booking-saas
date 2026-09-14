package com.example.booking.tenantdata.resource;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResourceResponse(
    UUID id,
    String name,
    ResourceType type,
    boolean active,
    OffsetDateTime createdAt,
    String description
) {
    public static ResourceResponse from(BookableResource resource) {
        return new ResourceResponse(
            resource.getId(),
            resource.getName(),
            resource.getType(),
            resource.isActive(),
            resource.getCreatedAt(),
            resource.getDescription()
        );
    }
}
