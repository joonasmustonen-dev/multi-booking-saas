package com.example.booking.tenantdata.dashboard;

import com.example.booking.tenantdata.appointment.AppointmentCalendarResponse;
import com.example.booking.tenantdata.appointment.AppointmentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DashboardSummaryResponse(
    String timeZone,
    LocalDate today,

    long todaysBookings,
    long openSlots,
    long customers,
    long bookingsThisWeek,

    List<DailyBookingCount> dailyBookings,
    List<AppointmentCalendarResponse> todaysAppointments,
    List<TeamMemberSummary> team,
    List<PopularServiceSummary> popularServices,
    List<StatusCount> statusCounts
) {
    public record DailyBookingCount(LocalDate date, long count) {}

    public record TeamMemberSummary(
        UUID staffId,
        String name,
        long todaysBookings
    ) {}

    public record PopularServiceSummary(
        UUID serviceId,
        String name,
        long bookings
    ) {}

    public record StatusCount(AppointmentStatus status, long count) {}
}
