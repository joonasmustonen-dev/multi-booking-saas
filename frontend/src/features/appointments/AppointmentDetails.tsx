import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { useQuery } from "@tanstack/react-query";
import { CalendarDays, Clock3, MapPin, UserRound, Package, Mail, Phone, FileText, X, CalendarClock, Check, Ban, CheckCheck } from "lucide-react";
import CustomerContext from "../customers/CustomerContext";
import { getCustomer } from "../customers/customerApi";
import { getServices } from "../services/serviceApi";
import { getAppointment } from "./appointmentApi";
import { formatDate, formatTime } from "../settings/timeFormat";
import type { AppointmentCalendarItem, AppointmentStatus } from "./appointmentTypes";
interface Props { appointment: AppointmentCalendarItem; timeZone: string; onClose: () => void; onReschedule: () => void; onCancel: () => Promise<unknown>; onStatus: (status: AppointmentStatus) => Promise<unknown> }
const statusNames: Record<AppointmentStatus, string> = { PENDING: "Pending approval", CONFIRMED: "Confirmed", CANCELLED: "Cancelled", COMPLETED: "Completed", NO_SHOW: "No show" };
export default function AppointmentDetails({ appointment, timeZone, onClose, onReschedule, onCancel, onStatus }: Props) {
    const customer = useQuery({ queryKey: ["customer", appointment.customerId], queryFn: () => getCustomer(appointment.customerId) });
    const detail = useQuery({ queryKey: ["appointment-detail", appointment.id], queryFn: () => getAppointment(appointment.id) });
    const services = useQuery({ queryKey: ["services"], queryFn: getServices });
    const [pending, setPending] = useState(false); const [error, setError] = useState(""); const close = useRef<HTMLButtonElement>(null);
    useEffect(() => { const previous = document.activeElement as HTMLElement | null; close.current?.focus(); return () => previous?.focus(); }, [appointment.id]);
    async function act(action: () => Promise<unknown>) { if (pending) return; setPending(true); setError(""); try { await action(); } catch (e) { setError(e instanceof Error ? e.message : "Could not update appointment."); } finally { setPending(false); } }
    function keyboard(event: KeyboardEvent<HTMLElement>) {
        if (event.key === "Escape") { event.stopPropagation(); onClose(); }
        if (event.key === "Tab") { const elements = [...event.currentTarget.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled)')]; const first = elements[0]; const last = elements.at(-1); if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); } }
    }
    const service = services.data?.find(service => service.id === appointment.serviceId);
    const duration = Math.round((Date.parse(appointment.endAt) - Date.parse(appointment.startAt)) / 60000);
    const active = appointment.status === "PENDING" || appointment.status === "CONFIRMED";
    const panel = <div className="appointment-drawer-backdrop" onMouseDown={onClose}><aside className="appointment-drawer appointment-detail-panel" role="dialog" aria-modal="true" aria-labelledby="appointment-detail-title" onKeyDown={keyboard} onMouseDown={event => event.stopPropagation()}>
        <header className="appointment-detail-hero"><div className="section-heading"><span className="page-eyebrow">Booking details</span><button ref={close} className="drawer-close" aria-label="Close appointment details" data-tooltip="Close details" onClick={onClose}><X size={19} /></button></div><span className={`appointment-status status-${appointment.status.toLowerCase()}`}>{statusNames[appointment.status]}</span><div className="appointment-customer-heading"><span className="customer-detail-avatar">{appointment.customerName.split(" ").map(part => part[0]).slice(0, 2).join("")}</span><div><h2 id="appointment-detail-title">{appointment.customerName}</h2><p>{appointment.serviceName}</p></div></div></header>
        <div className="appointment-detail-body"><section className="appointment-time-card"><CalendarDays size={22} /><div><strong>{formatDate(appointment.startAt, timeZone)}</strong><p>{formatTime(appointment.startAt, timeZone)} — {formatTime(appointment.endAt, timeZone)}</p><small>{duration} minutes · {timeZone}</small></div></section>
            <div className="appointment-assignment-tiles"><div><UserRound size={19} /><small>Staff member</small><strong>{appointment.staffName || "No staff assigned"}</strong></div><div><MapPin size={19} /><small>Location</small><strong>{appointment.locationName || "No location"}</strong></div><div><Package size={19} /><small>Resource</small><strong>{appointment.resourceName || "No resource"}</strong></div>{service?.price != null && <div><Clock3 size={19} /><small>Service price</small><strong>{service.price.toFixed(2)} {service.currency ?? ""}</strong></div>}</div>
            <section className="appointment-info-section"><h3>Customer contact</h3>{customer.isPending ? <p className="field-note">Loading contact details…</p> : customer.error ? <p className="field-note">Contact details could not be loaded.</p> : <div className="appointment-contact-list"><div><Mail size={16} />{customer.data.email ? <a href={`mailto:${customer.data.email}`}>{customer.data.email}</a> : <span>No email added</span>}</div><div><Phone size={16} />{customer.data.phone ? <a href={`tel:${customer.data.phone}`}>{customer.data.phone}</a> : <span>No phone added</span>}</div></div>}</section>
            <CustomerContext customerId={appointment.customerId} timeZone={timeZone} />
            <section className="appointment-info-section"><h3><FileText size={17} /> Notes</h3><p className="appointment-note">{detail.isPending ? "Loading notes…" : detail.error ? "Notes could not be loaded." : detail.data.notes || "No notes added."}</p></section>
            {detail.data?.createdAt && <p className="field-note">Booked {formatDate(detail.data.createdAt, timeZone)}</p>}{error && <p role="alert" className="form-error">{error}</p>}
        </div><footer className="appointment-detail-actions">{pending && <p className="field-note" role="status">Updating appointment…</p>}{active && <button className="button button-secondary" disabled={pending} onClick={onReschedule}><CalendarClock size={16} />Reschedule</button>}{appointment.status === "PENDING" && <button className="button button-primary" disabled={pending} onClick={() => act(() => onStatus("CONFIRMED"))}><Check size={16} />Confirm booking</button>}{appointment.status === "CONFIRMED" && <><button className="button button-primary" disabled={pending} onClick={() => act(() => onStatus("COMPLETED"))}><CheckCheck size={16} />Mark completed</button><button className="button button-secondary" disabled={pending} onClick={() => act(() => onStatus("NO_SHOW"))}>No show</button></>}{active && <button className="button button-danger" disabled={pending} onClick={() => { if (window.confirm(`Cancel ${appointment.customerName}'s appointment?`)) void act(onCancel); }}><Ban size={16} />Cancel booking</button>}{!active && <p className="field-note">This appointment is {statusNames[appointment.status].toLowerCase()}.</p>}</footer>
    </aside></div>;
    return typeof document !== "undefined" ? createPortal(panel, document.body) : panel;
}
