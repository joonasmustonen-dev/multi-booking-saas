package com.example.booking.tenantdata.waitlist;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateWaitlistEntryRequest(
    @NotNull UUID customerId,
    @NotNull UUID serviceId,
    UUID preferredStaffId,
    UUID preferredLocationId,
    @NotNull LocalDateTime windowStart,
    @NotNull LocalDateTime windowEnd,
    LocalDateTime expiresAt,
    boolean notificationConsent
) {
    @AssertTrue(message = "Waitlist end must be after its start")
    public boolean isWindowValid() {
        return windowStart == null || windowEnd == null || windowEnd.isAfter(windowStart);
    }
}
