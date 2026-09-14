import { apiFetch } from "../../api/apiClient";
import type { Assignment, AssignmentKind } from "./assignmentTypes";
export const getStaff = () => apiFetch<Assignment[]>("/api/v1/staff");
export const getLocations = () => apiFetch<Assignment[]>("/api/v1/locations");
export const createAssignment = (kind: "staff" | "locations", name: string) =>
    apiFetch<Assignment>(`/api/v1/${kind}`, {
        method: "POST",
        body: JSON.stringify({ name })
    });
export const updateAssignment = (
    kind: "staff" | "locations",
    id: string,
    name: string,
    active: boolean
) =>
    apiFetch<Assignment>(`/api/v1/${kind}/${id}`, {
        method: "PUT",
        body: JSON.stringify({ name, active })
    });
export const setAssignmentActive = (
    kind: AssignmentKind,
    id: string,
    active: boolean
) =>
    apiFetch<Assignment>(`/api/v1/${kind}/${id}/active`, {
        method: "PATCH",
        body: JSON.stringify({ active })
    });
