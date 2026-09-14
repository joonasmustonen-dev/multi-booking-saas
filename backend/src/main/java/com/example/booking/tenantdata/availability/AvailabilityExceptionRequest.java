package com.example.booking.tenantdata.availability;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AvailabilityExceptionRequest(

        @NotNull
        LocalDateTime startAt,

        @NotNull
        LocalDateTime endAt,

        boolean available
) {
}