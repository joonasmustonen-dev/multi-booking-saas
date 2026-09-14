export interface TenantSettings {
    timeZone: string;
    businessName: string;
    contactEmail: string;
    contactPhone: string;
    defaultCurrency: string;
    slotIntervalMinutes: number;
    minimumNoticeMinutes: number;
    bookingHorizonDays: number;
    calendarStartHour: number;
    calendarEndHour: number;
    weekStartsOn: number;
    defaultAppointmentStatus: "PENDING" | "CONFIRMED";
}
