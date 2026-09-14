import { useState } from "react";

import {
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query";

import {
    createCustomer,
    deleteCustomer,
    getCustomers,
    updateCustomer,
} from "./customerApi";

import type {
    Customer,
    CreateCustomerRequest,
} from "./customerTypes";

import CustomerForm from "./CustomerForm";

export default function CustomersPage() {
    const queryClient =
        useQueryClient();

    const [creating, setCreating] =
        useState(false);

    const [editingCustomer, setEditingCustomer] =
        useState<Customer | null>(null);

    const customersQuery =
        useQuery({
            queryKey: ["customers"],
            queryFn: getCustomers,
        });

    const createMutation =
        useMutation({
            mutationFn: createCustomer,

            onSuccess: async () => {
                await refreshCustomers();
                setCreating(false);
            },
        });

    const updateMutation =
        useMutation({
            mutationFn: ({
                id,
                request,
            }: {
                id: string;
                request: CreateCustomerRequest;
            }) =>
                updateCustomer(
                    id,
                    request
                ),

            onSuccess: async () => {
                await refreshCustomers();
                setEditingCustomer(null);
            },
        });

    const deleteMutation =
        useMutation({
            mutationFn: deleteCustomer,

            onSuccess: async () => {
                await refreshCustomers();
            },
        });

    async function refreshCustomers() {
        await queryClient.invalidateQueries({
            queryKey: ["customers"],
        });
    }

    async function handleCreate(
        request: CreateCustomerRequest
    ) {
        await createMutation.mutateAsync(
            request
        );
    }

    async function handleUpdate(
        request: CreateCustomerRequest
    ) {
        if (!editingCustomer) {
            return;
        }

        await updateMutation.mutateAsync({
            id: editingCustomer.id,
            request,
        });
    }

    function handleDelete(
        customer: Customer
    ) {
        const confirmed =
            window.confirm(
                `Delete ${customer.firstName} ${customer.lastName}?`
            );

        if (!confirmed) {
            return;
        }

        deleteMutation.mutate(
            customer.id
        );
    }

    if (customersQuery.isPending) {
        return (
            <p>Loading customers...</p>
        );
    }

    if (customersQuery.isError) {
        return (
            <div>
                <h2>Could not load customers</h2>

                <p>
                    {customersQuery.error.message}
                </p>
            </div>
        );
    }

    return (
        <div>
            <div>
                <h2>Customers</h2>

                <button
                    onClick={() => {
                        setEditingCustomer(null);
                        setCreating(true);
                    }}
                >
                    Add customer
                </button>
            </div>

            {creating && (
                <CustomerForm
                    onSubmit={handleCreate}
                    onCancel={() =>
                        setCreating(false)
                    }
                />
            )}

            {editingCustomer && (
                <CustomerForm
                    customer={editingCustomer}
                    onSubmit={handleUpdate}
                    onCancel={() =>
                        setEditingCustomer(null)
                    }
                />
            )}

            {createMutation.isError && (
                <p>
                    {createMutation.error.message}
                </p>
            )}

            {updateMutation.isError && (
                <p>
                    {updateMutation.error.message}
                </p>
            )}

            {deleteMutation.isError && (
                <p>
                    {deleteMutation.error.message}
                </p>
            )}

            {customersQuery.data.length === 0 ? (
                <p>No customers yet.</p>
            ) : (
                <ul>
                    {customersQuery.data.map(
                        (customer) => (
                            <li key={customer.id}>
                                <div>
                                    <strong>
                                        {customer.firstName}
                                        {" "}
                                        {customer.lastName}
                                    </strong>
                                </div>

                                <div>
                                    {customer.email
                                        ?? "No email"}
                                </div>

                                <div>
                                    {customer.phone
                                        ?? "No phone"}
                                </div>

                                <button
                                    onClick={() => {
                                        setCreating(false);
                                        setEditingCustomer(
                                            customer
                                        );
                                    }}
                                >
                                    Edit
                                </button>

                                <button
                                    onClick={() =>
                                        handleDelete(
                                            customer
                                        )
                                    }
                                    disabled={
                                        deleteMutation.isPending
                                    }
                                >
                                    Delete
                                </button>
                            </li>
                        )
                    )}
                </ul>
            )}
        </div>
    );
}