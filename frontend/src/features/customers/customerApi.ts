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