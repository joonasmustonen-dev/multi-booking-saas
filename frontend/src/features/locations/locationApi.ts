import { apiFetch } from "../../api/apiClient";
import type { Assignment } from "../assignments/assignmentTypes";
export interface LocationDetails extends Assignment {
    description: string;
    addressLine: string;
    city: string;
    postalCode: string;
    countryCode: string;
    phone: string;
}
export type LocationValues = Omit<LocationDetails, "id" | "createdAt">;
export const getLocations = () =>
    apiFetch<LocationDetails[]>("/api/v1/locations");
export const saveLocation = (values: LocationValues, id?: string) =>
    apiFetch<LocationDetails>(`/api/v1/locations${id ? `/${id}` : ""}`, {
        method: id ? "PUT" : "POST",
        body: JSON.stringify(values)
    });
