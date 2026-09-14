package com.example.booking.tenantdata.appointment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentResponse(
    UUID id,

    UUID customerId,
    UUID serviceId,
    UUID resourceId,
    UUID staffId,
    UUID locationId,

    OffsetDateTime startAt,
    OffsetDateTime endAt,

    AppointmentStatus status,

    String staffName,
    String locationName,
    String notes,

    OffsetDateTime createdAt
) {
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
            appointment.getId(),

            appointment.getCustomer().getId(),

            appointment.getService().getId(),

            appointment.getResource() != null
                ? appointment.getResource().getId()
                : null,

            appointment.getStaff() != null
                ? appointment.getStaff().getId()
                : null,

            appointment.getLocation() != null
                ? appointment.getLocation().getId()
                : null,

            appointment.getStartAt(),
            appointment.getEndAt(),

            appointment.getStatus(),

            appointment.getStaff() != null
                ? appointment.getStaff().getName()
                : null,

            appointment.getLocation() != null
                ? appointment.getLocation().getName()
                : null,

            appointment.getCustomer().isProcessingRestricted() &&
                !com.example.booking.security.ApiPermissions.isTenantAdmin()
                ? null
                : appointment.getNotes(),

            appointment.getCreatedAt()
        );
    }
}
