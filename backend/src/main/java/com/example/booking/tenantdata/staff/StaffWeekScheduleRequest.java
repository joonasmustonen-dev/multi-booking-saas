package com.example.booking.tenantdata.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
public record StaffWeekScheduleRequest(
    @NotNull @Size(max = 28) List<@NotNull @Valid Shift> shifts
) {
    public record Shift(
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
    ) {}
}
