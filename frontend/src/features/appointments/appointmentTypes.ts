export type AppointmentStatus =
    "PENDING" | "CONFIRMED" | "CANCELLED" | "COMPLETED" | "NO_SHOW";

export interface AppointmentCalendarItem {
    id: string;
    customerId: string;
    customerName: string;
    staffId: string | null;
    locationId: string | null;
    resourceId: string | null;
    resourceName: string | null;
    serviceId: string;
    serviceName: string;
    startAt: string;
    endAt: string;
    status: AppointmentStatus;

    staffName?: string | null;
    locationName?: string | null;
}

export interface CreateAppointmentRequest {
    customerId: string;
    serviceId: string;
    staffId: string | null;
    locationId: string | null;
    resourceId: string | null;
    startAt: string;
    notes: string | null;
}

export interface AppointmentResponse {
    id: string;
    customerId: string;
    serviceId: string;
    staffId: string | null;
    locationId: string | null;
    resourceId: string | null;
    startAt: string;
    endAt: string;
    status: AppointmentStatus;
    notes: string | null;
    createdAt: string;
}

export interface AvailabilitySlot {
    staffId: string | null;
    locationId: string | null;
    resourceId: string | null;
    start: string;
    end: string;
}
export interface RescheduleAppointmentRequest {
    staffId: string | null;
    locationId: string | null;
    resourceId: string | null;
    startAt: string;
}
