import { useDeferredValue, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { Plus, Search, Mail, Phone, UserRound } from "lucide-react";
import {
    createCustomer,
    updateCustomer,
    searchCustomers,
    deleteCustomer
} from "./customerApi";
import type { Customer, CreateCustomerRequest } from "./customerTypes";
import CustomerForm from "./CustomerForm";
import CustomerDetail from "./CustomerDetail";
import { useTenantSettings } from "../settings/useTenantSettings";

export default function CustomersPage() {
    const client = useQueryClient();
    const [params, setParams] = useSearchParams();
    const [search, setSearch] = useState("");
    const deferred = useDeferredValue(search);
    const [creating, setCreating] = useState(false);
    const [editing, setEditing] = useState<Customer | null>(null);
    const settings = useTenantSettings();
    const selectedId = params.get("customerId") ?? "";
    const results = useQuery({
        queryKey: ["customer-search", deferred, 50],
        queryFn: () => searchCustomers(deferred, 50)
    });
    async function remove(id: string) {
        await deleteCustomer(id);
        await Promise.all(
            [
                "customers",
                "customer-search",
                "customer",
                "customer-activity"
            ].map(key => client.invalidateQueries({ queryKey: [key] }))
        );
        setParams({});
        setEditing(null);
    }
    async function save(request: CreateCustomerRequest) {
        const saved = editing
            ? await updateCustomer(editing.id, request)
            : await createCustomer(request);
        await Promise.all(
            [
                "customers",
                "customer-search",
                "customer",
                "customer-activity"
            ].map(key => client.invalidateQueries({ queryKey: [key] }))
        );
        setCreating(false);
        setEditing(null);
        setParams({ customerId: saved.id });
    }
    return (
        <div className="customers-page">
            <header className="page-header">
                <div>
                    <span className="page-eyebrow">
                        Your customer directory
                    </span>
                    <h1>Customers</h1>
                    <p className="page-subtitle">
                        Quick lookup, visit history, and staff preferences.
                    </p>
                </div>
                <button
                    className="button button-primary"
                    onClick={() => {
                        setEditing(null);
                        setCreating(true);
                    }}
                >
                    <Plus size={18} />
                    Add customer
                </button>
            </header>
            {(creating || editing) && (
                <CustomerForm
                    key={editing?.id ?? "new"}
                    customer={editing ?? undefined}
                    onSubmit={save}
                    onCancel={() => {
                        setCreating(false);
                        setEditing(null);
                    }}
                />
            )}
            <label className="customer-search">
                <Search size={18} />
                <input
                    className="input"
                    aria-label="Search customers"
                    maxLength={100}
                    placeholder="Search name, phone number, or email…"
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                />
            </label>
            <p className="field-note">
                Showing up to 50 matches. Narrow your search to find someone
                quickly.
            </p>
            {results.isFetching && (
                <p role="status" className="field-note">
                    Searching customers…
                </p>
            )}
            {results.error && (
                <p role="alert" className="form-error">
                    {results.error.message}
                </p>
            )}
            <div
                className={`customer-directory ${selectedId ? "with-detail" : ""}`}
            >
                <div className="customer-cards">
                    {results.data?.map(c => (
                        <button
                            type="button"
                            className={`customer-card ${selectedId === c.id ? "selected" : ""}`}
                            key={c.id}
                            onClick={() => setParams({ customerId: c.id })}
                        >
                            <span className="customer-detail-avatar">
                                <UserRound size={22} />
                            </span>
                            <span>
                                <strong>
                                    {c.firstName} {c.lastName}
                                </strong>
                                <small>
                                    <Phone size={14} />
                                    {c.phone || "No phone added"}
                                </small>
                                <small>
                                    <Mail size={14} />
                                    {c.email || "No email added"}
                                </small>
                            </span>
                        </button>
                    ))}
                    {results.data?.length === 0 && (
                        <div className="customer-empty">
                            No customers match your search.
                        </div>
                    )}
                </div>
                {selectedId && settings.data && (
                    <CustomerDetail
                        key={selectedId}
                        id={selectedId}
                        timeZone={settings.data.timeZone}
                        onEdit={c => {
                            setCreating(false);
                            setEditing(c);
                        }}
                        onDelete={remove}
                        onClose={() => setParams({})}
                    />
                )}
            </div>
            {settings.error && (
                <p className="form-error" role="alert">
                    Customer history timezone could not be loaded.
                </p>
            )}
        </div>
    );
}
