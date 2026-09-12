package com.example.booking.tenantdata.appointment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateAppointmentRequest(

        @NotNull
        UUID customerId,

        @NotNull
        UUID serviceId,

        @NotNull
        UUID resourceId,

        @NotNull
        OffsetDateTime startAt,

        @Size(max = 2000)
        String notes
) {
}