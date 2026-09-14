import { useDeferredValue, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import SearchSelect from "../../components/SearchSelect";
import { getCustomer, searchCustomers } from "./customerApi";

export default function CustomerPicker({
    value,
    onChange
}: {
    value: string;
    onChange: (id: string) => void;
}) {
    const [query, setQuery] = useState("");
    const deferred = useDeferredValue(query);
    const results = useQuery({
        queryKey: ["customer-search", deferred, "booking"],
        queryFn: () => searchCustomers(deferred, 25, true),
        staleTime: 30000
    });
    const selected = useQuery({
        queryKey: ["customer", value],
        queryFn: () => getCustomer(value),
        enabled: !!value
    });
    const customers = results.data ?? [];
    const options = customers.map(c => ({
        value: c.id,
        label: `${c.firstName} ${c.lastName}`,
        description: [c.phone, c.email].filter(Boolean).join(" · ")
    }));
    return (
        <>
            <SearchSelect
                required
                value={value}
                options={options}
                onChange={onChange}
                selectedLabel={
                    selected.data
                        ? `${selected.data.firstName} ${selected.data.lastName}`
                        : undefined
                }
                onSearchChange={setQuery}
                filterLocally={false}
                loading={results.isFetching || query !== deferred}
                ariaLabel="Booking customer"
                placeholder="Search name, phone, or email…"
            />
            <small className="field-note">
                Up to 25 matches. Type more letters or digits to narrow the
                results.
            </small>
            {(results.error || selected.error) && (
                <p className="form-error" role="alert">
                    Customer lookup failed. Try again.
                </p>
            )}
        </>
    );
}
