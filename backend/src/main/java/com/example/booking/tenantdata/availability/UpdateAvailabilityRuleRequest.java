package com.example.booking.tenantdata.availability;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
public record UpdateAvailabilityRuleRequest(@NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime, @NotNull LocalTime endTime, @NotNull Boolean active) {}
