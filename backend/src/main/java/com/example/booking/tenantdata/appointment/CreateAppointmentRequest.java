package com.example.booking.tenantdata.appointment;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateAppointmentRequest(
    @NotNull UUID customerId,

    @NotNull UUID serviceId,

    UUID staffId,

    UUID locationId,

    UUID resourceId,

    @NotNull OffsetDateTime startAt,

    @jakarta.validation.constraints.Size(max = 2000) String notes
) {}
