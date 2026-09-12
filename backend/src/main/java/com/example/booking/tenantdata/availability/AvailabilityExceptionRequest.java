package com.example.booking.tenantdata.availability;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record AvailabilityExceptionRequest(

        @NotNull
        OffsetDateTime startAt,

        @NotNull
        OffsetDateTime endAt,

        boolean available
) {
}