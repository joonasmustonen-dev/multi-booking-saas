import { apiFetch } from "../../api/apiClient";

import type { BookableResource, ResourceRequest } from "./resourceTypes";

export function getResources() {
    return apiFetch<BookableResource[]>("/api/v1/resources");
}

export function getResource(id: string) {
    return apiFetch<BookableResource>(`/api/v1/resources/${id}`);
}

export function createResource(request: ResourceRequest) {
    return apiFetch<BookableResource>("/api/v1/resources", {
        method: "POST",
        body: JSON.stringify(request)
    });
}

export function updateResource(id: string, request: ResourceRequest) {
    return apiFetch<BookableResource>(`/api/v1/resources/${id}`, {
        method: "PUT",
        body: JSON.stringify(request)
    });
}

export function deleteResource(id: string) {
    return apiFetch<void>(`/api/v1/resources/${id}`, {
        method: "DELETE"
    });
}
