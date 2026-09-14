import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { useTenantSettings } from "../settings/useTenantSettings";
import OwnerSchedule from "../availability/OwnerSchedule";
import { Package, Plus, Pencil, Trash2 } from "lucide-react";
import { createResource, updateResource, deleteResource, getResources } from "./resourceApi";
import type { BookableResource, ResourceRequest } from "./resourceTypes";
import ResourceForm from "./ResourceForm";
import { setAssignmentActive } from "../assignments/assignmentApi";
import { invalidateBookingData } from "../assignments/invalidateBookingData";
export default function ResourcesPage() {
    const [params] = useSearchParams(); const [scheduleId, setScheduleId] = useState(params.get("ownerId") ?? ""); const settings = useTenantSettings();
    const client = useQueryClient(); const query = useQuery({ queryKey: ["resources"], queryFn: getResources });
    const [editing, setEditing] = useState<BookableResource | "new" | null>(null); const [pending, setPending] = useState(false); const [error, setError] = useState("");
    async function save(values: ResourceRequest) { if (editing && editing !== "new") await updateResource(editing.id, values); else await createResource(values); await invalidateBookingData(client); setEditing(null); }
    async function mutate(action: () => Promise<unknown>) { setPending(true); setError(""); try { await action(); await invalidateBookingData(client); } catch (e) { setError(e instanceof Error ? e.message : "Could not update resource."); } finally { setPending(false); } }
    if (query.isPending || settings.isPending) return <p>Loading resources…</p>; if (query.error || settings.error) return <p className="form-error" role="alert">{(query.error ?? settings.error)?.message}</p>;
    return <div className="management-page"><div className="page-header"><div><p className="page-eyebrow">Tools for every booking</p><h1 className="page-title">Resources</h1><p className="page-description">Manage your equipment, vehicles, and other bookable things.</p></div><button className="button button-primary" onClick={() => setEditing("new")}><Plus size={17} />Add resource</button></div>
        {editing && <ResourceForm key={editing === "new" ? "new" : editing.id} resource={editing === "new" ? undefined : editing} onSubmit={save} onCancel={() => setEditing(null)} />}{error && <p className="form-error" role="alert">{error}</p>}
        {query.data.length === 0 ? <div className="card empty-state"><Package size={30} /><h3>No resources yet</h3><p>Add a resource and set its availability here.</p></div> : <div className="service-grid">{query.data.map(resource => <article className="card service-card resource-card" key={resource.id}><div className="section-heading"><h3><Package size={20} />{resource.name}</h3><span className={`requirement-pill ${resource.active ? "sage" : "muted"}`}>{resource.active ? "Active" : "Inactive"}</span></div><p className="detail-line">{resource.type === "EQUIPMENT" ? "Equipment" : resource.type === "VEHICLE" ? "Vehicle" : "Other"}</p>{resource.description && <p className="field-note resource-description">{resource.description}</p>}<div className="form-actions"><button className="button button-secondary" onClick={() => { setScheduleId(resource.id); document.getElementById("resources-schedule")?.scrollIntoView({ behavior: "smooth" }); }}>Availability</button><button className="button button-secondary" onClick={() => setEditing(resource)}><Pencil size={15} />Edit</button><button className="button button-secondary" disabled={pending} onClick={() => mutate(() => setAssignmentActive("resources", resource.id, !resource.active))}>{resource.active ? "Deactivate" : "Activate"}</button><button className="button button-danger" data-tooltip="Delete resource" aria-label="Delete resource" disabled={pending} onClick={() => { if (window.confirm(`Delete ${resource.name}? Deactivate it instead to keep it for future use.`)) void mutate(() => deleteResource(resource.id)); }}><Trash2 size={15} />Delete</button></div></article>)}</div>}
        {settings.data && <OwnerSchedule kind="resources" options={query.data} selectedId={scheduleId} onSelect={setScheduleId} timeZone={settings.data.timeZone} />}
    </div>;
}