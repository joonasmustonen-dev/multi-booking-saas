package com.example.booking.tenantdata.availability;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AvailabilityExceptionResponse(
        UUID id,
        UUID staffId,
        UUID locationId,
        UUID resourceId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        boolean available
) {

    public static AvailabilityExceptionResponse from(
            AvailabilityException exception) {

        return new AvailabilityExceptionResponse(
                exception.getId(),
                exception.getStaff() != null ? exception.getStaff().getId() : null,
                exception.getLocation() != null ? exception.getLocation().getId() : null,
                exception.getResource() != null ? exception.getResource().getId() : null,
                exception.getStartAt(),
                exception.getEndAt(),
                exception.isAvailable()
        );
    }
}