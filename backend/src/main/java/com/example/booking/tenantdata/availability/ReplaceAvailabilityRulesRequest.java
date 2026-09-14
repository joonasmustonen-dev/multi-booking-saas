package com.example.booking.tenantdata.availability;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record ReplaceAvailabilityRulesRequest(
    @NotNull @Size(max = 28) List<@NotNull @Valid Rule> rules
) {
    public record Rule(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull Boolean active
    ) {}
}
