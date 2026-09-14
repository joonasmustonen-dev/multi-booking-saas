import SearchSelect from "../../components/SearchSelect";
import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Building2, CalendarDays, Clock3, Save } from "lucide-react";
import keycloak from "../../auth/keycloak";
import { useTenantSettings } from "./useTenantSettings";
import { updateTenantSettings } from "./settingsApi";
import type { TenantSettings } from "./settingsTypes";
import { invalidateBookingData } from "../assignments/invalidateBookingData";

function SettingsEditor({ settings, editable }: { settings: TenantSettings; editable: boolean }) {
    const client = useQueryClient();
    const [values, setValues] = useState(settings);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const [saved, setSaved] = useState(false);
    const set = <K extends keyof TenantSettings>(key: K, value: TenantSettings[K]) => { setValues(previous => ({ ...previous, [key]: value })); setSaved(false); };
    async function submit(event: FormEvent) {
        event.preventDefault(); setError(""); setSaved(false);
        if (values.calendarEndHour <= values.calendarStartHour) { setError("Calendar end hour must follow start hour."); return; }
        setPending(true);
        try {
            const updated = await updateTenantSettings(values);
            client.setQueryData(["tenant-settings"], updated);
            await invalidateBookingData(client); setSaved(true);
        } catch (e) { setError(e instanceof Error ? e.message : "Could not save settings."); }
        finally { setPending(false); }
    }
    const numberField = (label: string, key: "slotIntervalMinutes" | "minimumNoticeMinutes" | "bookingHorizonDays" | "calendarStartHour" | "calendarEndHour", min: number, max: number) => <label className="form-field">{label}<input className="input" type="number" required min={min} max={max} value={values[key]} onChange={e => set(key, Number(e.target.value))} /></label>;
    return <form className="settings-form" onSubmit={submit}>
        <fieldset disabled={!editable || pending} className="settings-sections">
            <section className="card management-panel tinted-panel"><div className="section-heading"><h2><Building2 size={20} /> Business details</h2><span className="requirement-pill sage">Workspace</span></div>
                <div className="form-grid"><label className="form-field">Business name<input className="input" required maxLength={150} value={values.businessName} onChange={e => set("businessName", e.target.value)} /></label>
                <label className="form-field">Default currency<input className="input" required maxLength={3} pattern="[A-Z]{3}" value={values.defaultCurrency} onChange={e => set("defaultCurrency", e.target.value.toUpperCase())} /></label>
                <label className="form-field">Contact email<input className="input" type="email" maxLength={254} value={values.contactEmail} onChange={e => set("contactEmail", e.target.value)} /></label>
                <label className="form-field">Contact phone<input className="input" type="tel" maxLength={40} value={values.contactPhone} onChange={e => set("contactPhone", e.target.value)} /></label></div>
                <p className="field-note">Your business name appears in the sidebar. Currency is the default for new services; existing service prices stay as saved.</p>
            </section>
            <section className="card management-panel tinted-panel sage-panel"><h2><Clock3 size={20} /> Booking policy</h2><div className="form-grid">
                <label className="form-field">Time zone<SearchSelect required disabled={!editable || pending} value={values.timeZone} onChange={value => set("timeZone", value)} ariaLabel="Workspace time zone" options={["UTC", ...Intl.supportedValuesOf("timeZone")].map(zone => ({ value: zone, label: zone }))} /></label>
                {numberField("Slot spacing (minutes)", "slotIntervalMinutes", 5, 120)}
                {numberField("Minimum booking notice (minutes)", "minimumNoticeMinutes", 0, 43200)}
                {numberField("Book ahead up to (days)", "bookingHorizonDays", 1, 730)}
                <label className="form-field">New booking status<SearchSelect searchable={false} disabled={!editable || pending} value={values.defaultAppointmentStatus} onChange={value => set("defaultAppointmentStatus", value as TenantSettings["defaultAppointmentStatus"])} ariaLabel="New booking status" options={[{ value: "PENDING", label: "Pending approval" }, { value: "CONFIRMED", label: "Confirmed immediately" }]} /></label>
            </div><p className="field-note">Notice and horizon are enforced for new bookings and rescheduling. Slot spacing controls offered start times. Changing time zone reinterprets recurring hours; existing appointment instants remain fixed.</p></section>
            <section className="card management-panel tinted-panel"><h2><CalendarDays size={20} /> Calendar display</h2><div className="form-grid">
                {numberField("Calendar starts at (hour, 0–23)", "calendarStartHour", 0, 23)}
                {numberField("Calendar ends at (hour, 1–24)", "calendarEndHour", 1, 24)}
                <label className="form-field">Week starts on<SearchSelect searchable={false} disabled={!editable || pending} value={String(values.weekStartsOn)} onChange={value => set("weekStartsOn", Number(value))} ariaLabel="Calendar week start" options={[{ value: "1", label: "Monday" }, { value: "0", label: "Sunday" }]} /></label>
            </div><p className="field-note">The calendar fits the viewport and expands its displayed hours when existing bookings fall outside this range.</p></section>
        </fieldset>
        {error && <p className="form-error" role="alert">{error}</p>}{saved && <p className="booking-summary" role="status">Settings saved.</p>}
        {editable ? <div className="form-actions"><button className="button button-primary" disabled={pending}><Save size={16} />{pending ? "Saving…" : "Save settings"}</button></div> : <p className="field-note">Only a tenant administrator can change workspace settings.</p>}
    </form>;
}
export default function SettingsPage() {
    const query = useTenantSettings();
    if (query.isPending) return <p>Loading settings…</p>;
    if (query.error) return <p className="form-error" role="alert">{query.error.message}</p>;
    const editable = keycloak.tokenParsed?.realm_access?.roles?.includes("TENANT_ADMIN") ?? false;
    return <div className="management-page"><div className="page-header"><div><p className="page-eyebrow">Your workspace</p><h1 className="page-title">Settings</h1><p className="page-description">Business details, booking policy, and your calendar.</p></div></div><SettingsEditor settings={query.data} editable={editable} /></div>;
}