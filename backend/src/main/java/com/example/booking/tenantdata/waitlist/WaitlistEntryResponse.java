package com.example.booking.tenantdata.waitlist;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WaitlistEntryResponse(
    UUID id,
    UUID customerId,
    String customerName,
    UUID serviceId,
    String serviceName,
    UUID preferredStaffId,
    String preferredStaffName,
    UUID preferredLocationId,
    String preferredLocationName,
    OffsetDateTime windowStart,
    OffsetDateTime windowEnd,
    OffsetDateTime expiresAt,
    WaitlistStatus status,
    boolean notificationConsent,
    OffsetDateTime consentRecordedAt,
    OffsetDateTime createdAt,
    WaitlistOfferResponse offer
) {
    static WaitlistEntryResponse from(WaitlistEntry entry, WaitlistOffer offer) {
        return new WaitlistEntryResponse(
            entry.getId(), entry.getCustomer().getId(),
            entry.getCustomer().getFirstName() + " " + entry.getCustomer().getLastName(),
            entry.getService().getId(), entry.getService().getName(),
            entry.getPreferredStaff() == null ? null : entry.getPreferredStaff().getId(),
            entry.getPreferredStaff() == null ? null : entry.getPreferredStaff().getName(),
            entry.getPreferredLocation() == null ? null : entry.getPreferredLocation().getId(),
            entry.getPreferredLocation() == null ? null : entry.getPreferredLocation().getName(),
            entry.getWindowStart(), entry.getWindowEnd(), entry.getExpiresAt(),
            entry.getStatus(), entry.isNotificationConsent(), entry.getConsentRecordedAt(),
            entry.getCreatedAt(), offer == null ? null : WaitlistOfferResponse.from(offer)
        );
    }
}
