package com.example.booking.tenantdata.appointment;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RescheduleAppointmentRequest(

        UUID staffId,

        UUID locationId,

        UUID resourceId,

        @NotNull
        OffsetDateTime startAt
) {
}