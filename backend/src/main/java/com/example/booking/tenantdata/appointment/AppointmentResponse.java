package com.example.booking.tenantdata.appointment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentResponse(

        UUID id,

        UUID customerId,
        UUID serviceId,
        UUID resourceId,

        OffsetDateTime startAt,
        OffsetDateTime endAt,

        AppointmentStatus status,

        String notes,

        OffsetDateTime createdAt
) {

    public static AppointmentResponse from(
            Appointment appointment) {

        return new AppointmentResponse(
                appointment.getId(),

                appointment.getCustomer().getId(),
                appointment.getService().getId(),
                appointment.getResource().getId(),

                appointment.getStartAt(),
                appointment.getEndAt(),

                appointment.getStatus(),

                appointment.getNotes(),

                appointment.getCreatedAt()
        );
    }
    
}