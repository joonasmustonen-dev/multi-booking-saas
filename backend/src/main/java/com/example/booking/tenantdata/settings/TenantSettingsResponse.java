package com.example.booking.tenantdata.settings;
public record TenantSettingsResponse(
        String timeZone,
        String businessName,
        String contactEmail,
        String contactPhone,
        String defaultCurrency,
        int slotIntervalMinutes,
        int minimumNoticeMinutes,
        int bookingHorizonDays,
        int calendarStartHour,
        int calendarEndHour,
        int weekStartsOn,
        String defaultAppointmentStatus
) {
    public TenantSettingsResponse(String timeZone) { this(timeZone, "Booking workspace", "", "", "EUR", 15, 0, 365, 8, 18, 1, "CONFIRMED"); }
    static TenantSettingsResponse from(TenantSettings entity) {
        return new TenantSettingsResponse(entity.getTimeZone(), entity.getBusinessName(), entity.getContactEmail(), entity.getContactPhone(), entity.getDefaultCurrency(), entity.getSlotIntervalMinutes(), entity.getMinimumNoticeMinutes(), entity.getBookingHorizonDays(), entity.getCalendarStartHour(), entity.getCalendarEndHour(), entity.getWeekStartsOn(), entity.getDefaultAppointmentStatus());
    }
}