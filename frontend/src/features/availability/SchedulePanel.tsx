import SearchSelect from "../../components/SearchSelect";
import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Pencil, Trash2 } from "lucide-react";
import type { AssignmentKind } from "../assignments/assignmentTypes";
import { invalidateBookingData } from "../assignments/invalidateBookingData";
import {
    getAvailabilityRules,
    getAvailabilityExceptions,
    saveAvailabilityException,
    deleteAvailabilityException
} from "./availabilityApi";
import type { AvailabilityException } from "./availabilityTypes";
import { formatDate, formatTime } from "../settings/timeFormat";
import { localDateTimeInZone } from "./scheduleDates";
import RecurringWeekEditor from "./RecurringWeekEditor";
interface Props {
    kind: AssignmentKind;
    ownerId: string;
    ownerName: string;
    timeZone: string;
    allowExtraAvailability?: boolean;
}
export default function SchedulePanel({
    kind,
    ownerId,
    ownerName,
    timeZone,
    allowExtraAvailability = true
}: Props) {
    const client = useQueryClient();
    const rules = useQuery({
        queryKey: ["availability-rules", kind, ownerId],
        queryFn: () => getAvailabilityRules(kind, ownerId)
    });
    const exceptions = useQuery({
        queryKey: ["availability-exceptions", kind, ownerId],
        queryFn: () => getAvailabilityExceptions(kind, ownerId)
    });
    const [editor, setEditor] = useState<AvailabilityException | "new" | null>(
        null
    );
    const [start, setStart] = useState("");
    const [end, setEnd] = useState("");
    const [available, setAvailable] = useState(false);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function mutate(action: () => Promise<unknown>, done?: () => void) {
        if (pending) return;
        setPending(true);
        setError("");
        try {
            await action();
            await invalidateBookingData(client);
            done?.();
        } catch (e) {
            setError(
                e instanceof Error ? e.message : "Could not save exception."
            );
        } finally {
            setPending(false);
        }
    }
    function edit(exception: AvailabilityException | "new") {
        setEditor(exception);
        setError("");
        setAvailable(exception !== "new" && exception.available);
        setStart(
            exception === "new"
                ? ""
                : localDateTimeInZone(exception.startAt, timeZone)
        );
        setEnd(
            exception === "new"
                ? ""
                : localDateTimeInZone(exception.endAt, timeZone)
        );
    }
    function submit(event: FormEvent) {
        event.preventDefault();
        if (end <= start) {
            setError("End time must be after start time.");
            return;
        }
        void mutate(
            () =>
                saveAvailabilityException(
                    kind,
                    ownerId,
                    { startAt: start, endAt: end, available },
                    editor && editor !== "new" ? editor.id : undefined
                ),
            () => setEditor(null)
        );
    }
    return (
        <>
            <section className="card management-panel">
                <div className="section-heading">
                    <div>
                        <h2>
                            {kind === "locations"
                                ? "Regular opening hours"
                                : "Recurring hours"}
                        </h2>
                        <p className="field-note">
                            {ownerName} · {timeZone}
                        </p>
                    </div>
                </div>
                {rules.isPending ? (
                    <p>Loading recurring hours…</p>
                ) : rules.error ? (
                    <p className="form-error" role="alert">
                        {rules.error.message}
                    </p>
                ) : (
                    <RecurringWeekEditor
                        key={`${kind}:${ownerId}`}
                        kind={kind}
                        ownerId={ownerId}
                        initialRules={rules.data}
                    />
                )}
            </section>
            <section className="card management-panel">
                <div className="section-heading">
                    <div>
                        <h2>
                            {kind === "locations" ? "Closures" : "Exceptions"}
                        </h2>
                        <p className="field-note">
                            {allowExtraAvailability
                                ? "Closures and extra availability"
                                : "Closing periods"}{" "}
                            · {timeZone}
                        </p>
                    </div>
                    <button
                        className="button button-secondary"
                        disabled={pending}
                        onClick={() => edit("new")}
                    >
                        <Plus size={16} />
                        Add {kind === "locations" ? "closure" : "exception"}
                    </button>
                </div>
                {error && (
                    <p className="form-error" role="alert">
                        {error}
                    </p>
                )}
                {editor && (
                    <form className="booking-form" onSubmit={submit}>
                        <fieldset className="editor-fields" disabled={pending}>
                            <div className="form-grid">
                                <label className="form-field">
                                    Starts
                                    <input
                                        className="input"
                                        required
                                        type="datetime-local"
                                        value={start}
                                        onChange={e => setStart(e.target.value)}
                                    />
                                </label>
                                <label className="form-field">
                                    Ends
                                    <input
                                        className="input"
                                        required
                                        type="datetime-local"
                                        value={end}
                                        onChange={e => setEnd(e.target.value)}
                                    />
                                </label>
                                <label className="form-field">
                                    Type
                                    <SearchSelect
                                        searchable={false}
                                        disabled={pending}
                                        value={
                                            available ? "available" : "blocked"
                                        }
                                        onChange={value =>
                                            setAvailable(value === "available")
                                        }
                                        ariaLabel="Availability exception type"
                                        options={[
                                            {
                                                value: "blocked",
                                                label: "Unavailable / closure"
                                            },
                                            ...(allowExtraAvailability ||
                                            (editor !== "new" &&
                                                !!editor.available)
                                                ? [
                                                      {
                                                          value: "available",
                                                          label: "Extra availability"
                                                      }
                                                  ]
                                                : [])
                                        ]}
                                    />
                                </label>
                            </div>
                        </fieldset>
                        <p className="field-note">
                            Closures take precedence over recurring hours and
                            extra availability.
                        </p>
                        <div className="form-actions">
                            <button
                                type="button"
                                className="button button-secondary"
                                disabled={pending}
                                onClick={() => setEditor(null)}
                            >
                                Cancel
                            </button>
                            <button
                                className="button button-primary"
                                disabled={pending}
                            >
                                {pending ? "Saving…" : "Save exception"}
                            </button>
                        </div>
                    </form>
                )}
                {exceptions.isPending ? (
                    <p>Loading exceptions…</p>
                ) : exceptions.error ? (
                    <p className="form-error" role="alert">
                        {exceptions.error.message}
                    </p>
                ) : exceptions.data.length === 0 ? (
                    <p className="field-note">No exceptions configured.</p>
                ) : (
                    <div className="schedule-list">
                        {exceptions.data.map(exception => (
                            <div className="schedule-row" key={exception.id}>
                                <div>
                                    <strong>
                                        {exception.available
                                            ? "Extra availability"
                                            : "Unavailable"}
                                    </strong>
                                    <span className="field-note">
                                        {formatDate(
                                            exception.startAt,
                                            timeZone
                                        )}{" "}
                                        {formatTime(
                                            exception.startAt,
                                            timeZone
                                        )}{" "}
                                        —{" "}
                                        {formatDate(exception.endAt, timeZone)}{" "}
                                        {formatTime(exception.endAt, timeZone)}
                                    </span>
                                </div>
                                <div className="form-actions">
                                    <button
                                        className="button button-secondary"
                                        disabled={pending}
                                        onClick={() => edit(exception)}
                                    >
                                        <Pencil size={14} />
                                        Edit
                                    </button>
                                    <button
                                        className="button button-danger"
                                        disabled={pending}
                                        onClick={() => {
                                            if (
                                                window.confirm(
                                                    "Delete this exception?"
                                                )
                                            )
                                                void mutate(() =>
                                                    deleteAvailabilityException(
                                                        kind,
                                                        ownerId,
                                                        exception.id
                                                    )
                                                );
                                        }}
                                    >
                                        <Trash2 size={14} />
                                        Delete
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </section>
        </>
    );
}
