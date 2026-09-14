import type { AssignmentCatalogs } from "./assignmentTypes";
export interface BookingAssignments { staffId: string | null; locationId: string | null; resourceId: string | null }
export function slotKey(slot: BookingAssignments & { start: string }) {
    return JSON.stringify([slot.staffId, slot.locationId, slot.resourceId, slot.start]);
}
export function assignmentNames(slot: BookingAssignments, catalogs: AssignmentCatalogs) {
    return ([
        [slot.staffId, catalogs.staff, "Staff"],
        [slot.locationId, catalogs.locations, "Location"],
        [slot.resourceId, catalogs.resources, "Resource"],
    ] as const).flatMap(([id, items, fallback]) => id ? [items.find(item => item.id === id)?.name ?? fallback] : []);
}
export function appointmentAssignmentLabel(item: { staffName?: string | null; locationName?: string | null; resourceName?: string | null }) {
    return [item.staffName, item.locationName, item.resourceName].filter(Boolean).join(" · ") || "No assignments";
}
