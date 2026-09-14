package com.example.booking.tenantdata.appointment;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.booking.tenantdata.resource.ResourceType;

public record AppointmentCalendarResponse(
    UUID id,

    UUID customerId,
    String customerName,

    UUID serviceId,
    String serviceName,

    UUID resourceId,
    String resourceName,

    OffsetDateTime startAt,
    OffsetDateTime endAt,

    AppointmentStatus status,

    String staffName,
    UUID staffId,

    String locationName,
    UUID locationId
) {
    public static AppointmentCalendarResponse from(Appointment appointment) {
        String customerName =
            appointment.getCustomer().getFirstName() +
            " " +
            appointment.getCustomer().getLastName();

        if (
            appointment.getCustomer().isProcessingRestricted() &&
            !com.example.booking.security.ApiPermissions.isTenantAdmin()
        ) customerName = "Restricted customer";

        return new AppointmentCalendarResponse(
            appointment.getId(),

            appointment.getCustomer().getId(),
            customerName,

            appointment.getService().getId(),
            appointment.getService().getName(),

            appointment.getResource() != null
                ? appointment.getResource().getId()
                : null,
            appointment.getResource() != null
                ? appointment.getResource().getName()
                : null,

            appointment.getStartAt(),
            appointment.getEndAt(),

            appointment.getStatus(),

            appointment.getStaff() != null
                ? appointment.getStaff().getName()
                : null,

            appointment.getStaff() != null
                ? appointment.getStaff().getId()
                : null,

            appointment.getLocation() != null
                ? appointment.getLocation().getName()
                : null,

            appointment.getLocation() != null
                ? appointment.getLocation().getId()
                : null
        );
    }
}
