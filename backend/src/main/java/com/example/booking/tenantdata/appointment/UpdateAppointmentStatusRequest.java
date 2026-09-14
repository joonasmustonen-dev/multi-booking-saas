package com.example.booking.tenantdata.appointment;

import jakarta.validation.constraints.NotNull;

public record UpdateAppointmentStatusRequest(
    @NotNull AppointmentStatus status
) {}
