import { apiFetch } from "../../api/apiClient";
import type { CreateWaitlistEntry, WaitlistEntry } from "./waitlistTypes";

export const getWaitlist = (customerId: string) =>
    apiFetch<WaitlistEntry[]>(
        `/api/v1/waitlist?${new URLSearchParams({ customerId })}`
    );
export const createWaitlistEntry = (request: CreateWaitlistEntry) =>
    apiFetch<WaitlistEntry>("/api/v1/waitlist", {
        method: "POST",
        body: JSON.stringify(request)
    });
export const acceptWaitlistOffer = (id: string) =>
    apiFetch<WaitlistEntry>(`/api/v1/waitlist/${id}/accept`, { method: "POST" });
export const expireWaitlistEntry = (id: string) =>
    apiFetch<WaitlistEntry>(`/api/v1/waitlist/${id}/expire`, { method: "POST" });
export const removeWaitlistEntry = (id: string) =>
    apiFetch<WaitlistEntry>(`/api/v1/waitlist/${id}`, { method: "DELETE" });
