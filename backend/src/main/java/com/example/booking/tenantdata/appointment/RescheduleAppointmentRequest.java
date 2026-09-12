package com.example.booking.tenantdata.appointment;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RescheduleAppointmentRequest(

        @NotNull
        UUID resourceId,

        @NotNull
        OffsetDateTime startAt

) {
}