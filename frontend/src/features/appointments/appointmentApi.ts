import { apiFetch } from "../../api/apiClient";

import type {
    AppointmentCalendarItem,
    AppointmentResponse,
    AppointmentStatus,
    AvailabilitySlot,
    CreateAppointmentRequest,
} from "./appointmentTypes";

export interface AppointmentFilters {
    staffId?: string;
    locationId?: string;
    resourceId?: string;
    customerId?: string;
    serviceId?: string;
    status?: AppointmentStatus;
}


export function getAppointments(
    from: string,
    to: string,
    filters: AppointmentFilters = {}
) {

    const params =
        new URLSearchParams({
            from,
            to,
        });


    if (filters.staffId) params.set("staffId", filters.staffId);
    if (filters.locationId) params.set("locationId", filters.locationId);

    if (
        filters.resourceId
    ) {
        params.set(
            "resourceId",
            filters.resourceId
        );
    }


    if (
        filters.customerId
    ) {
        params.set(
            "customerId",
            filters.customerId
        );
    }


    if (
        filters.serviceId
    ) {
        params.set(
            "serviceId",
            filters.serviceId
        );
    }


    if (
        filters.status
    ) {
        params.set(
            "status",
            filters.status
        );
    }


    return apiFetch<
        AppointmentCalendarItem[]
    >(
        `/api/v1/appointments/calendar?${params.toString()}`
    );
}

export function createAppointment(
    request: CreateAppointmentRequest
) {
    return apiFetch<AppointmentResponse>(
        "/api/v1/appointments",
        {
            method: "POST",
            body: JSON.stringify(request),
        }
    );
}

export function cancelAppointment(
    appointmentId: string
) {
    return apiFetch<AppointmentResponse>(
        `/api/v1/appointments/${appointmentId}/cancel`,
        {
            method: "POST",
        }
    );
}

export interface AvailabilityFilters { staffId?: string; locationId?: string; resourceId?: string }
function availabilityParams(from: string, to: string, filters: AvailabilityFilters) {
    const params = new URLSearchParams({ from, to });
    for (const [key, value] of Object.entries(filters)) if (value) params.set(key, value);
    return params;
}
export function getAvailability(serviceId: string, date: string, filters: AvailabilityFilters = {}) {
    const params = availabilityParams(date, date, filters); params.set("serviceId", serviceId);
    return apiFetch<AvailabilitySlot[]>(`/api/v1/availability?${params}`);
}
export function getRescheduleAvailability(id: string, date: string, filters: AvailabilityFilters = {}) {
    return apiFetch<AvailabilitySlot[]>(`/api/v1/appointments/${id}/availability?${availabilityParams(date, date, filters)}`);
}
export function rescheduleAppointment(id: string, request: import("./appointmentTypes").RescheduleAppointmentRequest) {
    return apiFetch<AppointmentResponse>(`/api/v1/appointments/${id}/reschedule`, { method: "POST", body: JSON.stringify(request) });
}

export function updateAppointmentStatus(
    appointmentId: string,
    status: AppointmentStatus
) {
    return apiFetch<AppointmentResponse>(
        `/api/v1/appointments/${appointmentId}/status`,
        {
            method: "PATCH",

            body: JSON.stringify({
                status,
            }),
        }
    );
}

export const getAppointment = (id: string) => apiFetch<AppointmentResponse>(`/api/v1/appointments/${id}`);
