import { apiFetch } from "../../api/apiClient";

import type {
    ServiceOffering,
    CreateServiceRequest,
    UpdateServiceRequest,
} from "./serviceTypes";

export function getServices() {
    return apiFetch<ServiceOffering[]>(
        "/api/v1/services"
    );
}

export function getService(id: string) {
    return apiFetch<ServiceOffering>(
        `/api/v1/services/${id}`
    );
}

export function createService(
    request: CreateServiceRequest
) {
    return apiFetch<ServiceOffering>(
        "/api/v1/services",
        {
            method: "POST",
            body: JSON.stringify(request),
        }
    );
}

export function updateService(
    id: string,
    request: UpdateServiceRequest
) {
    return apiFetch<ServiceOffering>(
        `/api/v1/services/${id}`,
        {
            method: "PUT",
            body: JSON.stringify(request),
        }
    );
}

export function deleteService(id: string) {
    return apiFetch<void>(
        `/api/v1/services/${id}`,
        {
            method: "DELETE",
        }
    );
}