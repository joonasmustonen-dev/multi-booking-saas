import { useState } from "react";
import { Plus, Pencil, Clock3 } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createService, deleteService, getServices, updateService } from "./serviceApi";
import { useAssignmentCatalogs } from "../assignments/useAssignmentCatalogs";
import { invalidateBookingData } from "../assignments/invalidateBookingData";
import type { ServiceOffering, UpdateServiceRequest } from "./serviceTypes";
import ServiceForm, { type ServiceFormValues } from "./ServiceForm";
import { useTenantSettings } from "../settings/useTenantSettings";
export default function ServicesPage() {
    const settings = useTenantSettings();
    const client = useQueryClient();
    const catalogs = useAssignmentCatalogs();
    const services = useQuery({ queryKey: ["services"], queryFn: getServices });
    const [editing, setEditing] = useState<ServiceOffering | "new" | null>(null);
    const save = useMutation({ mutationFn: ({ id, values }: { id?: string; values: UpdateServiceRequest }) => id ? updateService(id, values) : createService(values), onSuccess: async () => { await invalidateBookingData(client); setEditing(null); } });
    const remove = useMutation({ mutationFn: deleteService, onSuccess: () => invalidateBookingData(client) });
    if (services.isPending || catalogs.isPending || settings.isPending) return <p>Loading services…</p>;
    if (services.error || catalogs.error || settings.error) return <p className="form-error" role="alert">{(services.error ?? catalogs.error ?? settings.error)?.message}</p>;
    function submit(values: ServiceFormValues) { return save.mutateAsync({ id: editing && editing !== "new" ? editing.id : undefined, values }).then(() => undefined); }
    return <div className="management-page">
        <div className="page-header"><div><p className="page-eyebrow">Your offering</p><h1 className="page-title">Services</h1><p className="page-description">The people, places, and resources behind every booking.</p></div><button className="button button-primary" onClick={() => setEditing("new")}><Plus size={17} />Add service</button></div>
        {editing && <ServiceForm key={editing === "new" ? "new" : editing.id} service={editing === "new" ? undefined : editing} catalogs={catalogs.data} onSubmit={submit} onCancel={() => setEditing(null)} />}
        {remove.error && <p role="alert" className="form-error">{remove.error.message}</p>}
        {services.data.length === 0 ? <div className="card empty-state"><h3>No services yet</h3><p>Add a service and define what each booking needs.</p></div> : <div className="service-grid">{services.data.map(service => <article className="card service-card" key={service.id}>
            <div className="section-heading"><h3>{service.name}</h3><span className={`requirement-pill ${service.active ? "sage" : "muted"}`}>{service.active ? "Active" : "Inactive"}</span></div>
            {service.description && <p className="field-note">{service.description}</p>}
            <p className="service-meta"><Clock3 size={15} />{service.durationMinutes} min <span>{service.price == null ? "No price" : `${service.price} ${service.currency ?? ""}`}</span></p>
            {([
                ["Staff", service.staffRequirement, service.staffIds, catalogs.data.staff],
                ["Locations", service.locationRequirement, service.locationIds, catalogs.data.locations],
                ["Resources", service.resourceRequirement, service.resourceIds, catalogs.data.resources],
            ] as const).map(([label, requirement, ids, items]) => <div className="service-assignment" key={label}><div><strong>{label}</strong><span className="requirement-pill">{requirement === "FORBIDDEN" ? "Not used" : requirement === "REQUIRED" ? "Required" : "Optional"}</span></div><p>{requirement === "FORBIDDEN" ? "—" : ids.map(id => { const item = items.find(i => i.id === id); return item ? `${item.name}${item.active ? "" : " (inactive)"}` : "Unavailable assignment"; }).join(", ") || "None selected"}</p></div>)}
            <div className="form-actions"><button className="button button-secondary" onClick={() => setEditing(service)}><Pencil size={15} />Edit</button><button className="button button-danger" disabled={remove.isPending} onClick={() => { if (window.confirm(`Delete ${service.name}?`)) remove.mutate(service.id); }}>Delete</button></div>
        </article>)}</div>}
    </div>;
}