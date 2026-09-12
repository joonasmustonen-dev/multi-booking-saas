package com.example.booking.tenantdata.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ServiceResponse(
        UUID id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency,
        boolean active,
        Set<UUID> resourceIds,
        OffsetDateTime createdAt
) {

    public static ServiceResponse from(ServiceOffering service) {

        Set<UUID> resources =
                service.getResources()
                        .stream()
                        .map(resource -> resource.getId())
                        .collect(Collectors.toSet());

        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getDurationMinutes(),
                service.getPrice(),
                service.getCurrency(),
                service.isActive(),
                resources,
                service.getCreatedAt()
        );
    }
}