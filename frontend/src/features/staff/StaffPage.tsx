import { canManageWorkspace } from "../../auth/permissions";
import { entityLabel } from "../assignments/entityLabels";
import CatalogBrowser from "../../components/CatalogBrowser";
import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import {
    CalendarDays,
    Mail,
    Phone,
    Plus,
    Users,
    Pencil,
    Trash2,
    MapPin
} from "lucide-react";
import {
    getStaff,
    saveStaff,
    removeStaff,
    type StaffMember,
    type StaffValues
} from "./staffApi";
import { useAssignmentCatalogs } from "../assignments/useAssignmentCatalogs";
import { invalidateBookingData } from "../assignments/invalidateBookingData";
import { useTenantSettings } from "../settings/useTenantSettings";
import SearchSelect, { SearchMultiSelect } from "../../components/SearchSelect";
import type { Assignment } from "../assignments/assignmentTypes";
import StaffWeekEditor from "./StaffWeekEditor";
import SchedulePanel from "../availability/SchedulePanel";
import { nextStaffWeek } from "./rotaDates";
import { dateKeyInTimeZone } from "../appointments/calendarLayout";
function StaffForm({
    member,
    locations,
    onSave,
    onCancel
}: {
    member?: StaffMember;
    locations: Assignment[];
    onSave: (values: StaffValues) => Promise<void>;
    onCancel: () => void;
}) {
    const [values, setValues] = useState<StaffValues>({
        name: member?.name ?? "",
        email: member?.email ?? "",
        phone: member?.phone ?? "",
        active: member?.active ?? true,
        freeAgent: member?.freeAgent ?? true,
        locationIds: member?.locationIds ?? []
    });
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        if (!values.freeAgent && values.locationIds.length === 0) {
            setError(
                "Choose at least one location, or allow work at any location."
            );
            return;
        }
        setPending(true);
        try {
            await onSave({
                ...values,
                name: values.name.trim(),
                locationIds: values.freeAgent ? [] : values.locationIds
            });
        } catch (e) {
            setError(e instanceof Error ? e.message : "Could not save staff.");
        } finally {
            setPending(false);
        }
    }
    return (
        <form
            className="card management-form tinted-panel sage-panel staff-form"
            onSubmit={submit}
        >
            <div className="section-heading">
                <h2>{member ? "Edit staff member" : "Add staff member"}</h2>
                <Users size={24} />
            </div>
            <fieldset
                className="editor-fields"
                disabled={pending || !canManageWorkspace()}
            >
                <div className="form-grid">
                    <label className="form-field">
                        Name
                        <input
                            className="input"
                            required
                            maxLength={150}
                            value={values.name}
                            onChange={e =>
                                setValues({ ...values, name: e.target.value })
                            }
                        />
                    </label>
                    <label className="form-field">
                        Email
                        <input
                            className="input"
                            type="email"
                            maxLength={254}
                            value={values.email}
                            onChange={e =>
                                setValues({ ...values, email: e.target.value })
                            }
                        />
                    </label>
                    <label className="form-field">
                        Phone
                        <input
                            className="input"
                            type="tel"
                            maxLength={40}
                            value={values.phone}
                            onChange={e =>
                                setValues({ ...values, phone: e.target.value })
                            }
                        />
                    </label>
                </div>
                <label
                    className="check-row"
                    data-tooltip="Free agents may work at any location allowed by the booked service. Location opening hours still apply."
                >
                    <input
                        type="checkbox"
                        checked={values.freeAgent}
                        onChange={e =>
                            setValues({
                                ...values,
                                freeAgent: e.target.checked,
                                locationIds: e.target.checked
                                    ? []
                                    : values.locationIds
                            })
                        }
                    />
                    Can work at any location · Free agent
                </label>
                {!values.freeAgent && (
                    <div className="form-field">
                        Assigned locations
                        <SearchMultiSelect
                            values={values.locationIds}
                            options={locations.map(location => ({
                                value: location.id,
                                label: entityLabel(location, locations),
                                description: location.active
                                    ? "Active location"
                                    : "Inactive location"
                            }))}
                            onChange={locationIds =>
                                setValues({ ...values, locationIds })
                            }
                            ariaLabel="Assigned staff locations"
                            disabled={pending || !canManageWorkspace()}
                        />
                    </div>
                )}
                <label className="check-row">
                    <input
                        type="checkbox"
                        checked={values.active}
                        onChange={e =>
                            setValues({ ...values, active: e.target.checked })
                        }
                    />
                    Staff member active
                </label>
            </fieldset>
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
            <div className="form-actions">
                <button
                    className="button button-secondary"
                    disabled={pending || !canManageWorkspace()}
                    type="button"
                    onClick={onCancel}
                >
                    Cancel
                </button>
                <button
                    className="button button-primary"
                    disabled={pending || !canManageWorkspace()}
                >
                    {pending ? "Saving…" : "Save staff member"}
                </button>
            </div>
        </form>
    );
}
export default function StaffPage() {
    const canManage = canManageWorkspace();
    const client = useQueryClient();
    const query = useQuery({ queryKey: ["staff"], queryFn: getStaff });
    const catalogs = useAssignmentCatalogs();
    const settings = useTenantSettings();
    const [params] = useSearchParams();
    const [editing, setEditing] = useState<StaffMember | "new" | null>(null);
    const [selectedId, setSelectedId] = useState(params.get("ownerId") ?? "");
    const [chosenWeek, setChosenWeek] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function save(values: StaffValues) {
        const saved = await saveStaff(
            values,
            editing && editing !== "new" ? editing.id : undefined
        );
        await invalidateBookingData(client);
        setSelectedId(saved.id);
        setEditing(null);
    }
    async function remove(member: StaffMember) {
        if (
            !window.confirm(
                `Remove ${member.name} from staff? Existing appointment history will be kept.`
            )
        )
            return;
        setPending(true);
        setError("");
        try {
            await removeStaff(member.id);
            await invalidateBookingData(client);
            if (selectedId === member.id) setSelectedId("");
        } catch (e) {
            setError(
                e instanceof Error ? e.message : "Could not remove staff."
            );
        } finally {
            setPending(false);
        }
    }
    function schedule(id: string) {
        setSelectedId(id);
        document
            .getElementById("staff-schedule")
            ?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
    if (query.isPending || catalogs.isPending || settings.isPending)
        return <p>Loading staff…</p>;
    const loadError = query.error ?? catalogs.error ?? settings.error;
    if (loadError)
        return (
            <p className="form-error" role="alert">
                {loadError.message}
            </p>
        );
    if (!query.data || !settings.data) return null;
    const selected =
        query.data.find(member => member.id === selectedId) ?? query.data[0];
    const timeZone = settings.data.timeZone;
    const nextWeek = nextStaffWeek(
        dateKeyInTimeZone(new Date().toISOString(), timeZone)
    );
    const weekStart = chosenWeek || nextWeek;
    return (
        <div className="management-page">
            <div className="page-header">
                <div>
                    <p className="page-eyebrow">People behind every booking</p>
                    <h1 className="page-title">Staff</h1>
                    <p className="page-description">
                        Contact details, assigned locations, and easy weekly
                        scheduling.
                    </p>
                </div>
                {canManage && (
                    <button
                        className="button button-primary"
                        onClick={() => setEditing("new")}
                    >
                        <Plus size={17} />
                        Add staff member
                    </button>
                )}
            </div>
            {canManage && editing && (
                <StaffForm
                    key={editing === "new" ? "new" : editing.id}
                    member={editing === "new" ? undefined : editing}
                    locations={catalogs.data.locations}
                    onSave={save}
                    onCancel={() => setEditing(null)}
                />
            )}
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
            {query.data.length === 0 ? (
                <div className="card empty-state">
                    <Users size={30} />
                    <h3>No staff yet</h3>
                    <p>
                        {canManage
                            ? "Add a staff member to build their schedule for next week."
                            : "No staff members have been configured."}
                    </p>
                </div>
            ) : (
                <>
                    <CatalogBrowser
                        items={query.data}
                        label="Staff"
                        searchText={item => `${item.email} ${item.phone}`}
                    >
                        {visible => (
                            <div className="service-grid">
                                {visible.map(member => (
                                    <article
                                        className="card service-card staff-card"
                                        key={member.id}
                                    >
                                        <div className="section-heading">
                                            <h3>
                                                <span className="staff-avatar">
                                                    {member.name
                                                        .slice(0, 1)
                                                        .toUpperCase()}
                                                </span>
                                                {entityLabel(
                                                    member,
                                                    query.data
                                                )}
                                            </h3>
                                            <span
                                                className={`requirement-pill ${member.active ? "sage" : "muted"}`}
                                            >
                                                {member.active
                                                    ? "Active"
                                                    : "Inactive"}
                                            </span>
                                        </div>
                                        <p className="detail-line">
                                            <Mail size={16} />
                                            {member.email || "No email added"}
                                        </p>
                                        <p className="detail-line">
                                            <Phone size={16} />
                                            {member.phone || "No phone added"}
                                        </p>
                                        <p className="detail-line">
                                            <MapPin size={16} />
                                            {member.freeAgent
                                                ? "Free agent · Any service location"
                                                : member.locationIds
                                                      .map(
                                                          id =>
                                                              catalogs.data.locations.find(
                                                                  location =>
                                                                      location.id ===
                                                                      id
                                                              )?.name ??
                                                              "Unavailable location"
                                                      )
                                                      .join(", ")}
                                        </p>
                                        <div className="form-actions">
                                            <button
                                                className="button button-secondary"
                                                onClick={() =>
                                                    schedule(member.id)
                                                }
                                            >
                                                <CalendarDays size={15} />
                                                Schedule
                                            </button>
                                            {canManage && <>
                                                <button
                                                    className="button button-secondary"
                                                    onClick={() =>
                                                        setEditing(member)
                                                    }
                                                >
                                                    <Pencil size={15} />
                                                    Edit
                                                </button>
                                                <button
                                                    className="button button-danger"
                                                    disabled={pending}
                                                    onClick={() => remove(member)}
                                                >
                                                    <Trash2 size={15} />
                                                    Remove
                                                </button>
                                            </>}
                                        </div>
                                    </article>
                                ))}
                            </div>
                        )}
                    </CatalogBrowser>
                    {selected && (
                        <div id="staff-schedule" className="owner-schedule">
                            <div className="schedule-picker">
                                <label className="form-field">
                                    Schedule for
                                    <SearchSelect
                                        value={selected.id}
                                        options={query.data.map(member => ({
                                            value: member.id,
                                            label: entityLabel(
                                                member,
                                                query.data
                                            ),
                                            description:
                                                member.email ||
                                                (member.active
                                                    ? "Active staff member"
                                                    : "Inactive staff member")
                                        }))}
                                        onChange={setSelectedId}
                                        ariaLabel="Staff member schedule"
                                    />
                                </label>
                            </div>
                            <StaffWeekEditor
                                staffId={selected.id}
                                staffName={selected.name}
                                timeZone={timeZone}
                                weekStart={weekStart}
                                onWeekChange={setChosenWeek}
                                nextWeek={nextWeek}
                            />
                            <details className="card regular-hours">
                                <summary>Recurring hours and time off</summary>
                                <div className="management-page">
                                    <SchedulePanel
                                        key={selected.id}
                                        kind="staff"
                                        ownerId={selected.id}
                                        ownerName={selected.name}
                                        timeZone={timeZone}
                                    />
                                </div>
                            </details>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
