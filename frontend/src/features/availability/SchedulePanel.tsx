import SearchSelect from "../../components/SearchSelect";
import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { AssignmentKind } from "../assignments/assignmentTypes";
import { invalidateBookingData } from "../assignments/invalidateBookingData";
import { getAvailabilityRules, getAvailabilityExceptions, saveAvailabilityRule, saveAvailabilityException, deleteAvailabilityRule, deleteAvailabilityException } from "./availabilityApi";
import type { AvailabilityRule, AvailabilityException, DayOfWeek } from "./availabilityTypes";
import { formatDate, formatTime } from "../settings/timeFormat";
import { localDateTimeInZone } from "./scheduleDates";
const days: DayOfWeek[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
interface Props { kind: AssignmentKind; ownerId: string; ownerName: string; timeZone: string; allowExtraAvailability?: boolean }
export default function SchedulePanel({ kind, ownerId, ownerName, timeZone, allowExtraAvailability = true }: Props) {
    const client = useQueryClient();
    const rules = useQuery({ queryKey: ["availability-rules", kind, ownerId], queryFn: () => getAvailabilityRules(kind, ownerId) });
    const exceptions = useQuery({ queryKey: ["availability-exceptions", kind, ownerId], queryFn: () => getAvailabilityExceptions(kind, ownerId) });
    const [ruleEditor, setRuleEditor] = useState<AvailabilityRule | "new" | null>(null);
    const [exceptionEditor, setExceptionEditor] = useState<AvailabilityException | "new" | null>(null);
    const [day, setDay] = useState<DayOfWeek>("MONDAY");
    const [start, setStart] = useState("09:00"); const [end, setEnd] = useState("17:00");
    const [active, setActive] = useState(true);
    const [exceptionStart, setExceptionStart] = useState(""); const [exceptionEnd, setExceptionEnd] = useState("");
    const [available, setAvailable] = useState(false);
    const [pending, setPending] = useState(false); const [error, setError] = useState("");
    async function mutate(action: () => Promise<unknown>, done?: () => void) {
        setPending(true); setError("");
        try { await action(); await invalidateBookingData(client); done?.(); }
        catch (e) { setError(e instanceof Error ? e.message : "Could not save availability."); }
        finally { setPending(false); }
    }
    function editRule(rule: AvailabilityRule | "new") {
        setRuleEditor(rule); setError(""); setDay(rule === "new" ? "MONDAY" : rule.dayOfWeek);
        setStart(rule === "new" ? "09:00" : rule.startTime.slice(0, 5)); setEnd(rule === "new" ? "17:00" : rule.endTime.slice(0, 5)); setActive(rule === "new" || rule.active);
    }
    function editException(exception: AvailabilityException | "new") {
        setExceptionEditor(exception); setError(""); setAvailable(exception !== "new" && exception.available);
        setExceptionStart(exception === "new" ? "" : localDateTimeInZone(exception.startAt, timeZone)); setExceptionEnd(exception === "new" ? "" : localDateTimeInZone(exception.endAt, timeZone));
    }
    function submitRule(event: FormEvent) {
        event.preventDefault(); if (end <= start) { setError("End time must be after start time."); return; }
        void mutate(() => saveAvailabilityRule(kind, ownerId, { dayOfWeek: day, startTime: start, endTime: end, active }, ruleEditor && ruleEditor !== "new" ? ruleEditor.id : undefined), () => setRuleEditor(null));
    }
    function submitException(event: FormEvent) {
        event.preventDefault(); if (exceptionEnd <= exceptionStart) { setError("End time must be after start time."); return; }
        void mutate(() => saveAvailabilityException(kind, ownerId, { startAt: exceptionStart, endAt: exceptionEnd, available }, exceptionEditor && exceptionEditor !== "new" ? exceptionEditor.id : undefined), () => setExceptionEditor(null));
    }
    return <>
        {error && <p className="form-error" role="alert">{error}</p>}
        <section className="card management-panel"><div className="section-heading"><div><h2>{kind === "locations" ? "Regular opening hours" : "Recurring hours"}</h2><p className="field-note">{ownerName} · {timeZone}</p></div><button className="button button-primary" disabled={pending} onClick={() => editRule("new")}>Add rule</button></div>
            {ruleEditor && <form className="booking-form" onSubmit={submitRule}><div className="form-grid"><label className="form-field">Day<SearchSelect searchable={false} disabled={pending} value={day} onChange={value => setDay(value as DayOfWeek)} ariaLabel="Recurring hours weekday" options={days.map(day => ({ value: day, label: day.charAt(0) + day.slice(1).toLowerCase() }))} /></label><label className="form-field">Start<input className="input" type="time" required value={start} onChange={e => setStart(e.target.value)} /></label><label className="form-field">End<input className="input" type="time" required value={end} onChange={e => setEnd(e.target.value)} /></label>{ruleEditor !== "new" && <label className="check-row"><input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} />Rule active</label>}</div><div className="form-actions"><button type="button" className="button button-secondary" onClick={() => setRuleEditor(null)}>Cancel</button><button className="button button-primary" disabled={pending}>{pending ? "Saving…" : "Save rule"}</button></div></form>}
            {rules.isPending ? <p>Loading recurring hours…</p> : rules.error ? <p role="alert" className="form-error">{rules.error.message}</p> : rules.data.length === 0 ? <p className="field-note">No recurring hours. Add a rule or extra availability to make this owner bookable.</p> : <div className="schedule-list">{[...rules.data].sort((a, b) => days.indexOf(a.dayOfWeek) - days.indexOf(b.dayOfWeek) || a.startTime.localeCompare(b.startTime)).map(rule => <div className="schedule-row" key={rule.id}><div><strong>{rule.dayOfWeek.charAt(0) + rule.dayOfWeek.slice(1).toLowerCase()}</strong><span className="field-note">{rule.startTime.slice(0, 5)}–{rule.endTime.slice(0, 5)}{!rule.active && " · Inactive"}</span></div><div className="form-actions"><button className="button button-secondary" disabled={pending} onClick={() => editRule(rule)}>Edit</button><button className="button button-danger" disabled={pending} onClick={() => { if (window.confirm("Delete this recurring rule?")) void mutate(() => deleteAvailabilityRule(kind, ownerId, rule.id)); }}>Delete</button></div></div>)}</div>}
        </section>
        <section className="card management-panel"><div className="section-heading"><div><h2>{kind === "locations" ? "Closures" : "Exceptions"}</h2><p className="field-note">{allowExtraAvailability ? "Closures and extra availability" : "Closing periods"} · {timeZone}</p></div><button className="button button-primary" disabled={pending} onClick={() => editException("new")}>Add exception</button></div>
            {exceptionEditor && <form className="booking-form" onSubmit={submitException}><div className="form-grid"><label className="form-field">Starts<input className="input" required type="datetime-local" value={exceptionStart} onChange={e => setExceptionStart(e.target.value)} /></label><label className="form-field">Ends<input className="input" required type="datetime-local" value={exceptionEnd} onChange={e => setExceptionEnd(e.target.value)} /></label><label className="form-field">Type<SearchSelect searchable={false} disabled={pending} value={available ? "available" : "blocked"} onChange={value => setAvailable(value === "available")} ariaLabel="Availability exception type" options={[{ value: "blocked", label: "Unavailable / closure" }, ...((allowExtraAvailability || (exceptionEditor !== "new" && !!exceptionEditor?.available)) ? [{ value: "available", label: "Extra availability" }] : [])]} /></label></div><p className="field-note">Closures take precedence over recurring hours and extra availability.</p><div className="form-actions"><button type="button" className="button button-secondary" onClick={() => setExceptionEditor(null)}>Cancel</button><button className="button button-primary" disabled={pending}>{pending ? "Saving…" : "Save exception"}</button></div></form>}
            {exceptions.isPending ? <p>Loading exceptions…</p> : exceptions.error ? <p role="alert" className="form-error">{exceptions.error.message}</p> : exceptions.data.length === 0 ? <p className="field-note">No exceptions configured.</p> : <div className="schedule-list">{exceptions.data.map(exception => <div className="schedule-row" key={exception.id}><div><strong>{exception.available ? "Extra availability" : "Unavailable"}</strong><span className="field-note">{formatDate(exception.startAt, timeZone)} {formatTime(exception.startAt, timeZone)} – {formatDate(exception.endAt, timeZone)} {formatTime(exception.endAt, timeZone)}</span></div><div className="form-actions"><button className="button button-secondary" disabled={pending} onClick={() => editException(exception)}>Edit</button><button className="button button-danger" disabled={pending} onClick={() => { if (window.confirm("Delete this exception?")) void mutate(() => deleteAvailabilityException(kind, ownerId, exception.id)); }}>Delete</button></div></div>)}</div>}
        </section>
    </>;
}
