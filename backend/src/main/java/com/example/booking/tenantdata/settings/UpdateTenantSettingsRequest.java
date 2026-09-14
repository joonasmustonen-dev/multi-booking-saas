package com.example.booking.tenantdata.settings;

import jakarta.validation.constraints.*;
public record UpdateTenantSettingsRequest(
    @NotBlank String timeZone,
    @Size(max = 150) String businessName,
    @Email @Size(max = 254) String contactEmail,
    @Size(max = 40) String contactPhone,
    @Pattern(regexp = "[A-Z]{3}") String defaultCurrency,
    @Min(5) @Max(120) Integer slotIntervalMinutes,
    @Min(0) @Max(43200) Integer minimumNoticeMinutes,
    @Min(1) @Max(730) Integer bookingHorizonDays,
    @Min(0) @Max(23) Integer calendarStartHour,
    @Min(1) @Max(24) Integer calendarEndHour,
    @Min(0) @Max(1) Integer weekStartsOn,
    @Pattern(regexp = "PENDING|CONFIRMED") String defaultAppointmentStatus
) {
    public UpdateTenantSettingsRequest(String timeZone) {
        this(
            timeZone,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
