import { apiFetch } from "../../api/apiClient";

import type {
    Customer,
    CreateCustomerRequest,
    UpdateCustomerRequest,
} from "./customerTypes";

export function getCustomers() {
    return apiFetch<Customer[]>(
        "/api/v1/customers"
    );
}

export function getCustomer(id: string) {
    return apiFetch<Customer>(
        `/api/v1/customers/${id}`
    );
}

export function createCustomer(
    request: CreateCustomerRequest
) {
    return apiFetch<Customer>(
        "/api/v1/customers",
        {
            method: "POST",
            body: JSON.stringify(request),
        }
    );
}

export function updateCustomer(
    id: string,
    request: UpdateCustomerRequest
) {
    return apiFetch<Customer>(
        `/api/v1/customers/${id}`,
        {
            method: "PUT",
            body: JSON.stringify(request),
        }
    );
}

export function deleteCustomer(id: string) {
    return apiFetch<void>(
        `/api/v1/customers/${id}`,
        {
            method: "DELETE",
        }
    );
}
export function searchCustomers(query = '', limit = 25) {
    const params = new URLSearchParams({ q: query.trim(), limit: String(limit) });
    return apiFetch<Customer[]>(`/api/v1/customers/search?${params}`);
}
export function getCustomerActivity(id: string, page = 0, size = 20) {
    return apiFetch<import('./customerTypes').CustomerActivity>(`/api/v1/customers/${id}/activity?page=${page}&size=${size}`);
}
