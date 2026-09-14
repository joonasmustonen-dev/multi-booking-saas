import { apiFetch } from "../../api/apiClient";
import type { Assignment } from "../assignments/assignmentTypes";
export interface StaffMember extends Assignment { email: string; phone: string; freeAgent: boolean; locationIds: string[] }
export type StaffValues = Omit<StaffMember, "id" | "createdAt">;
export interface StaffShift { date: string; startTime: string; endTime: string }
export interface StaffWeek { weekStart: string; overridden: boolean; shifts: StaffShift[] }
export const getStaff = () => apiFetch<StaffMember[]>("/api/v1/staff");
export const saveStaff = (values: StaffValues, id?: string) => apiFetch<StaffMember>(`/api/v1/staff${id ? `/${id}` : ""}`, { method: id ? "PUT" : "POST", body: JSON.stringify(values) });
export const removeStaff = (id: string) => apiFetch<void>(`/api/v1/staff/${id}`, { method: "DELETE" });
const scheduleUrl = (id: string, weekStart: string) => `/api/v1/staff/${id}/schedule?${new URLSearchParams({ weekStart })}`;
export const getStaffWeek = (id: string, weekStart: string) => apiFetch<StaffWeek>(scheduleUrl(id, weekStart));
export const saveStaffWeek = (id: string, weekStart: string, shifts: StaffShift[]) => apiFetch<StaffWeek>(scheduleUrl(id, weekStart), { method: "PUT", body: JSON.stringify({ shifts }) });
export const resetStaffWeek = (id: string, weekStart: string) => apiFetch<void>(scheduleUrl(id, weekStart), { method: "DELETE" });
export const staffValues = (staff: StaffMember): StaffValues => ({ name: staff.name, email: staff.email ?? "", phone: staff.phone ?? "", active: staff.active, freeAgent: staff.freeAgent, locationIds: staff.locationIds ?? [] });
