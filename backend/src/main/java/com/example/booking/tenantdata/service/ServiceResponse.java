package com.example.booking.tenantdata.service;

import com.example.booking.tenantdata.resource.ResourceType;
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

        Set<UUID> staffIds,
        Set<UUID> locationIds,
        Set<UUID> resourceIds,

        AssignmentRequirement staffRequirement,
        AssignmentRequirement locationRequirement,
        AssignmentRequirement resourceRequirement,
        OffsetDateTime createdAt
) {

    public static ServiceResponse from(
            ServiceOffering service) {

        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getDurationMinutes(),
                service.getPrice(),
                service.getCurrency(),
                service.isActive(),

                service.getStaff()
                        .stream()
                        .map(staff ->
                                staff.getId()
                        )
                        .collect(
                                Collectors.toSet()
                        ),

                service.getLocations()
                        .stream()
                        .map(location ->
                                location.getId()
                        )
                        .collect(
                                Collectors.toSet()
                        ),

                service.getResources()
                        .stream()
                        /*
                         * During V7 STAFF and ROOM still
                         * exist as legacy resources.
                         *
                         * Do not expose those through the
                         * new resourceIds API.
                         */
                        .filter(resource ->
                                resource.getType()
                                        != ResourceType.STAFF
                                &&
                                resource.getType()
                                        != ResourceType.ROOM
                        )
                        .map(resource ->
                                resource.getId()
                        )
                        .collect(
                                Collectors.toSet()
                        ),

                service.getStaffRequirement(),
                service.getLocationRequirement(),
                service.getResourceRequirement(),
                service.getCreatedAt()
        );
    }
}