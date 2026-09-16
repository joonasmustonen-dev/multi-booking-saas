package com.example.booking.tenantdata.waitlist;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WaitlistOfferResponse(
    UUID id,
    UUID staffId,
    String staffName,
    UUID locationId,
    String locationName,
    UUID resourceId,
    String resourceName,
    OffsetDateTime startAt,
    OffsetDateTime endAt,
    WaitlistStatus status,
    NotificationChannel notificationChannel,
    OffsetDateTime notificationQueuedAt,
    OffsetDateTime expiresAt,
    UUID appointmentId
) {
    static WaitlistOfferResponse from(WaitlistOffer offer) {
        return new WaitlistOfferResponse(
            offer.getId(),
            offer.getStaff() == null ? null : offer.getStaff().getId(),
            offer.getStaff() == null ? null : offer.getStaff().getName(),
            offer.getLocation() == null ? null : offer.getLocation().getId(),
            offer.getLocation() == null ? null : offer.getLocation().getName(),
            offer.getResource() == null ? null : offer.getResource().getId(),
            offer.getResource() == null ? null : offer.getResource().getName(),
            offer.getStartAt(), offer.getEndAt(), offer.getStatus(),
            offer.getNotificationChannel(), offer.getNotificationQueuedAt(),
            offer.getExpiresAt(),
            offer.getAppointment() == null ? null : offer.getAppointment().getId()
        );
    }
}
