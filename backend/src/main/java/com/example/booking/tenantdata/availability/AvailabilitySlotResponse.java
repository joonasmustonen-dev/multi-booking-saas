package com.example.booking.tenantdata.availability;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AvailabilitySlotResponse(
        UUID resourceId,
        OffsetDateTime start,
        OffsetDateTime end
) {
}