package com.example.booking.tenantdata.availability;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AvailabilitySlotResponse(
        UUID staffId,
        UUID locationId,
        UUID resourceId,
        OffsetDateTime start,
        OffsetDateTime end
) {
}