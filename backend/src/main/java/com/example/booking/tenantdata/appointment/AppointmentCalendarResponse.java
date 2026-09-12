package com.example.booking.tenantdata.appointment;

import java.time.OffsetDateTime;
import java.util.UUID;

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

        AppointmentStatus status

) {

    public static AppointmentCalendarResponse from(
            Appointment appointment) {

        String customerName =
                appointment.getCustomer().getFirstName()
                        + " "
                        + appointment.getCustomer().getLastName();

        return new AppointmentCalendarResponse(
                appointment.getId(),

                appointment.getCustomer().getId(),
                customerName,

                appointment.getService().getId(),
                appointment.getService().getName(),

                appointment.getResource().getId(),
                appointment.getResource().getName(),

                appointment.getStartAt(),
                appointment.getEndAt(),

                appointment.getStatus()
        );
    }
}