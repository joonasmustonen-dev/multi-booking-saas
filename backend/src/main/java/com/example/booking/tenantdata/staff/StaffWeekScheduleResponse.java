package com.example.booking.tenantdata.staff;
import java.time.LocalDate;
import java.util.List;
public record StaffWeekScheduleResponse(LocalDate weekStart, boolean overridden,
        List<StaffWeekScheduleRequest.Shift> shifts) {}
