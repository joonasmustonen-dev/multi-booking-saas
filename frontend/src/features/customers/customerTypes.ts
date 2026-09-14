export interface Customer {
    id: string;
    firstName: string;
    lastName: string;
    email: string | null;
    phone: string | null;
    createdAt: string;
}

export interface CreateCustomerRequest {
    firstName: string;
    lastName: string;
    email: string | null;
    phone: string | null;
}

export interface UpdateCustomerRequest {
    firstName: string;
    lastName: string;
    email: string | null;
    phone: string | null;
}