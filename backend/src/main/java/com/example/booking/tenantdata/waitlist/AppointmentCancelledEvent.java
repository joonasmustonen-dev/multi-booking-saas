package com.example.booking.tenantdata.waitlist;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentCancelledEvent(
    UUID serviceId,
    UUID staffId,
    UUID locationId,
    UUID resourceId,
    OffsetDateTime startAt,
    OffsetDateTime endAt
) {}
