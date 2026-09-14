import SearchSelect from "../../components/SearchSelect";
import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { getCustomers } from "../customers/customerApi";
import { getServices } from "../services/serviceApi";
import { useAssignmentCatalogs } from "../assignments/useAssignmentCatalogs";
import AssignmentFilters, { type AssignmentFilterValues } from "../assignments/AssignmentFilters";
import { assignmentNames, slotKey, appointmentAssignmentLabel } from "../assignments/assignmentLabels";
import { useTenantSettings } from "../settings/useTenantSettings";
import { formatTime } from "../settings/timeFormat";
import { dateKeyInTimeZone } from "./calendarLayout";
import { addDays } from "./dateUtils";
import { getAvailability, getRescheduleAvailability } from "./appointmentApi";
import type { AppointmentCalendarItem, CreateAppointmentRequest } from "./appointmentTypes";
interface Props { appointment?: AppointmentCalendarItem; onSubmit: (request: CreateAppointmentRequest) => Promise<void>; onCancel: () => void }
const emptyFilters: AssignmentFilterValues = { staffId: "", locationId: "", resourceId: "" };
export default function BookingEditor({ appointment, onSubmit, onCancel }: Props) {
    const catalogs = useAssignmentCatalogs(); const settings = useTenantSettings();
    const services = useQuery({ queryKey: ["services"], queryFn: getServices });
    const customers = useQuery({ queryKey: ["customers"], queryFn: getCustomers });
    const [customerId, setCustomerId] = useState(appointment?.customerId ?? "");
    const [serviceId, setServiceId] = useState(appointment?.serviceId ?? "");
    const [filters, setFilters] = useState<AssignmentFilterValues>({ staffId: appointment?.staffId ?? "", locationId: appointment?.locationId ?? "", resourceId: appointment?.resourceId ?? "" });
    const [date, setDate] = useState(""); const [selectedKey, setSelectedKey] = useState("");
    const [notes, setNotes] = useState(""); const [submitting, setSubmitting] = useState(false); const [error, setError] = useState("");
    const service = services.data?.find(s => s.id === serviceId);
    const timeZone = settings.data?.timeZone;
    const requested = {
        staffId: service?.staffRequirement !== "FORBIDDEN" && service?.staffIds.includes(filters.staffId) && catalogs.data.staff.some(item => item.id === filters.staffId && item.active) ? filters.staffId : "",
        locationId: service?.locationRequirement !== "FORBIDDEN" && service?.locationIds.includes(filters.locationId) && catalogs.data.locations.some(item => item.id === filters.locationId && item.active) ? filters.locationId : "",
        resourceId: service?.resourceRequirement !== "FORBIDDEN" && service?.resourceIds.includes(filters.resourceId) && catalogs.data.resources.some(item => item.id === filters.resourceId && item.active) ? filters.resourceId : "",
    };
    const availability = useQuery({
        queryKey: ["availability", appointment ? "reschedule" : "create", serviceId, appointment?.id, date, requested.staffId, requested.locationId, requested.resourceId],
        queryFn: () => appointment ? getRescheduleAvailability(appointment.id, date, requested) : getAvailability(serviceId, date, requested),
        enabled: !!service?.active && !!date && !!timeZone && !catalogs.isPending && !catalogs.error,
        staleTime: 0,
    });
    const scope = JSON.stringify([serviceId, date, requested]);
    const selected = availability.data?.find(slot => `${scope}:${slotKey(slot)}` === selectedKey);
    const ready = !!selected && !availability.isFetching && !availability.error && !!service?.active;
    async function submit(event: FormEvent) {
        event.preventDefault(); if (!ready || !selected || !customerId) return;
        setSubmitting(true); setError("");
        try { await onSubmit({ customerId, serviceId, staffId: selected.staffId, locationId: selected.locationId, resourceId: selected.resourceId, startAt: selected.start, notes: notes.trim() || null }); }
        catch (e) { setError(e instanceof Error ? e.message : "Could not save appointment."); setSelectedKey(""); await availability.refetch(); }
        finally { setSubmitting(false); }
    }
    if (catalogs.isPending || settings.isPending || services.isPending || customers.isPending) return <p>Loading booking options…</p>;
    const loadError = catalogs.error ?? settings.error ?? services.error ?? customers.error;
    if (loadError) return <div><p role="alert" className="form-error">{loadError.message}</p><button className="button button-secondary" onClick={onCancel}>Close</button></div>;
    if (!settings.data || !services.data || !customers.data) return <p>Loading booking options…</p>;
    const zone = settings.data.timeZone;
    const today = dateKeyInTimeZone(new Date().toISOString(), zone);
    return <form className="booking-form" onSubmit={submit}>
        <h3>{appointment ? "Reschedule appointment" : "New appointment"}</h3>
        <p className="field-note">All times are shown in {zone}.</p>
        {appointment ? <div className="booking-summary">{appointment.customerName} · {appointment.serviceName}<br />Current: {formatTime(appointment.startAt, zone)} · {appointmentAssignmentLabel(appointment)}</div> : <div className="form-grid">
            <div className="form-field">Customer<SearchSelect required value={customerId} onChange={setCustomerId} ariaLabel="Booking customer" placeholder="Type to find a customer…" options={customers.data.map(customer => ({ value: customer.id, label: `${customer.firstName} ${customer.lastName}`, description: customer.email ?? undefined }))} /></div>
            <div className="form-field">Service<SearchSelect required value={serviceId} onChange={value => { setServiceId(value); setFilters(emptyFilters); setSelectedKey(""); setError(""); }} ariaLabel="Booking service" placeholder="Type to find a service…" options={services.data.filter(service => service.active).map(service => ({ value: service.id, label: service.name, description: `${service.durationMinutes} minutes` }))} /></div>
        </div>}
        {service && <AssignmentFilters service={service} catalogs={catalogs.data} values={requested} onChange={value => { setFilters(value); setSelectedKey(""); setError(""); }} />}
        {appointment && !service && <p role="alert" className="form-error">This service is no longer available.</p>}
        {service && !service.active && <p role="alert" className="form-error">This service is inactive. Activate it before rescheduling.</p>}
        <label className="form-field">{appointment ? "New date" : "Date"}<input className="input" type="date" required min={today} max={addDays(today, settings.data.bookingHorizonDays)} value={date} onChange={e => { setDate(e.target.value); setSelectedKey(""); setError(""); }} /></label>
        {availability.isFetching && <p className="field-note" aria-live="polite">Finding available combinations…</p>}
        {availability.error && <p className="form-error" role="alert">{availability.error.message}</p>}
        {service && date && availability.data && <div className="form-field">Available time and assignments<SearchSelect required ariaLabel="Available booking combination" value={selected ? selectedKey : ""} disabled={availability.isFetching} onChange={setSelectedKey} placeholder="Type a time, staff member, or location…" options={availability.data.map(slot => ({ value: `${scope}:${slotKey(slot)}`, label: `${formatTime(slot.start, zone)}–${formatTime(slot.end, zone)} · ${assignmentNames(slot, catalogs.data).join(" · ")}${service.resourceRequirement === "OPTIONAL" && !slot.resourceId ? " · No resource" : ""}` }))} /></div>}
        {service && date && availability.data?.length === 0 && !availability.isFetching && <p className="field-note">No available combinations for this date. Try another date or broaden the assignment filters.</p>}
        {selected && <div className="booking-summary">{formatTime(selected.start, zone)}–{formatTime(selected.end, zone)}<br />{assignmentNames(selected, catalogs.data).join(" · ")}</div>}
        {!appointment && <label className="form-field">Notes<textarea className="textarea" value={notes} onChange={e => setNotes(e.target.value)} /></label>}
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="form-actions"><button className="button button-secondary" type="button" onClick={onCancel}>Cancel</button><button className="button button-primary" disabled={submitting || !ready || !customerId}>{submitting ? "Saving…" : appointment ? "Reschedule" : "Create appointment"}</button></div>
    </form>;
}
