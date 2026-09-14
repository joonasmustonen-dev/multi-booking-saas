package com.example.booking.tenantdata.customer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.example.booking.tenantdata.appointment.AppointmentStatus;

public record CustomerActivityResponse(
    CustomerResponse customer,
    String preferredStaffName,
    long totalBookings,
    long completedBookings,
    long cancelledBookings,
    long noShows,
    OffsetDateTime lastVisit,
    List<FrequentService> mostBookedServices,
    List<Booking> bookings,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public record FrequentService(UUID serviceId, String name, long visits) {}

    public record Booking(
        UUID id,
        UUID serviceId,
        String serviceName,
        String staffName,
        String locationName,
        String resourceName,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        AppointmentStatus status
    ) {}
}
