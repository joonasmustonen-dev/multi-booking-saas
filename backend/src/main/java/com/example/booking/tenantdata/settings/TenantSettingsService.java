package com.example.booking.tenantdata.settings;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;

@Service
public class TenantSettingsService {

    private static final int SETTINGS_ID = 1;

    private final TenantSettingsRepository repository;

    public TenantSettingsService(
            TenantSettingsRepository repository) {

        this.repository = repository;
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public TenantSettingsResponse getSettings() {

        TenantSettings settings =
                getSettingsEntity();

        return TenantSettingsResponse.from(settings);
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public ZoneId getZoneId() {

        return ZoneId.of(
                getSettingsEntity()
                        .getTimeZone()
        );
    }

    @Transactional("tenantTransactionManager")
    public TenantSettingsResponse update(
            UpdateTenantSettingsRequest request) {

        final ZoneId zone;

        try {

            zone = ZoneId.of(
                    request.timeZone()
            );

        } catch (DateTimeException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid time zone",
                    e
            );
        }

        TenantSettings settings =
                getSettingsEntity();

        settings.setTimeZone(
                zone.getId()
        );

        if (request.businessName() != null) settings.setBusinessName(request.businessName().trim());
        if (request.contactEmail() != null) settings.setContactEmail(request.contactEmail().trim());
        if (request.contactPhone() != null) settings.setContactPhone(request.contactPhone().trim());
        if (request.defaultCurrency() != null) settings.setDefaultCurrency(request.defaultCurrency().trim());
        if (request.slotIntervalMinutes() != null) settings.setSlotIntervalMinutes(request.slotIntervalMinutes());
        if (request.minimumNoticeMinutes() != null) settings.setMinimumNoticeMinutes(request.minimumNoticeMinutes());
        if (request.bookingHorizonDays() != null) settings.setBookingHorizonDays(request.bookingHorizonDays());
        if (request.calendarStartHour() != null) settings.setCalendarStartHour(request.calendarStartHour());
        if (request.calendarEndHour() != null) settings.setCalendarEndHour(request.calendarEndHour());
        if (request.weekStartsOn() != null) settings.setWeekStartsOn(request.weekStartsOn());
        if (request.defaultAppointmentStatus() != null) settings.setDefaultAppointmentStatus(request.defaultAppointmentStatus().trim());
        if (settings.getBusinessName().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business name is required");
        if (settings.getCalendarEndHour() <= settings.getCalendarStartHour())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calendar end hour must follow start hour");
        try { java.util.Currency.getInstance(settings.getDefaultCurrency()); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid currency"); }
        return TenantSettingsResponse.from(settings);
    }

    public void validateBookingStart(java.time.OffsetDateTime start) {
        TenantSettingsResponse policy = getSettings();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        if (!start.isAfter(now) || start.isBefore(now.plusMinutes(policy.minimumNoticeMinutes())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking starts before the minimum notice period");
        if (start.atZoneSameInstant(getZoneId()).toLocalDate().isAfter(java.time.LocalDate.now(getZoneId()).plusDays(policy.bookingHorizonDays())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is beyond the booking horizon");
    }

    private TenantSettings getSettingsEntity() {

        return repository.findById(SETTINGS_ID)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Tenant settings are missing"
                        )
                );
    }
}