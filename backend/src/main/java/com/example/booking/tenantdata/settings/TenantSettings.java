package com.example.booking.tenantdata.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    @Id
    private Integer id;

    @Column(
            name = "time_zone",
            nullable = false
    )
    private String timeZone;

    @Column(name = "business_name", nullable = false)
    private String businessName = "Booking workspace";

    @Column(name = "contact_email", nullable = false)
    private String contactEmail = "";

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone = "";

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency = "EUR";

    @Column(name = "slot_interval_minutes", nullable = false)
    private int slotIntervalMinutes = 15;

    @Column(name = "minimum_notice_minutes", nullable = false)
    private int minimumNoticeMinutes = 0;

    @Column(name = "booking_horizon_days", nullable = false)
    private int bookingHorizonDays = 365;

    @Column(name = "calendar_start_hour", nullable = false)
    private int calendarStartHour = 8;

    @Column(name = "calendar_end_hour", nullable = false)
    private int calendarEndHour = 18;

    @Column(name = "week_starts_on", nullable = false)
    private int weekStartsOn = 1;

    @Column(name = "default_appointment_status", nullable = false)
    private String defaultAppointmentStatus = "CONFIRMED";

    protected TenantSettings() {
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(
            String timeZone) {

        this.timeZone = timeZone;
    }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String value) { this.businessName = value; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String value) { this.contactEmail = value; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String value) { this.contactPhone = value; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String value) { this.defaultCurrency = value; }
    public int getSlotIntervalMinutes() { return slotIntervalMinutes; }
    public void setSlotIntervalMinutes(int value) { this.slotIntervalMinutes = value; }
    public int getMinimumNoticeMinutes() { return minimumNoticeMinutes; }
    public void setMinimumNoticeMinutes(int value) { this.minimumNoticeMinutes = value; }
    public int getBookingHorizonDays() { return bookingHorizonDays; }
    public void setBookingHorizonDays(int value) { this.bookingHorizonDays = value; }
    public int getCalendarStartHour() { return calendarStartHour; }
    public void setCalendarStartHour(int value) { this.calendarStartHour = value; }
    public int getCalendarEndHour() { return calendarEndHour; }
    public void setCalendarEndHour(int value) { this.calendarEndHour = value; }
    public int getWeekStartsOn() { return weekStartsOn; }
    public void setWeekStartsOn(int value) { this.weekStartsOn = value; }
    public String getDefaultAppointmentStatus() { return defaultAppointmentStatus; }
    public void setDefaultAppointmentStatus(String value) { this.defaultAppointmentStatus = value; }
}