package com.example.booking.tenantdata.availability;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityRuleRequest(
    @NotNull DayOfWeek dayOfWeek,

    @NotNull LocalTime startTime,

    @NotNull LocalTime endTime
) {}
