import DateTimeInput from "../../components/DateTimeInput";
import { canManageWorkspace } from "../../auth/permissions";
import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
    ChevronLeft,
    ChevronRight,
    Copy,
    Plus,
    Save,
    Trash2,
    Check
} from "lucide-react";
import { addDays } from "../appointments/dateUtils";
import {
    getStaffWeek,
    saveStaffWeek,
    resetStaffWeek,
    type StaffShift,
    type StaffWeek
} from "./staffApi";
import { copyShiftsToWeek } from "./rotaDates";
import { invalidateBookingData } from "../assignments/invalidateBookingData";
import { formatDate } from "../settings/timeFormat";

function ReadOnlyWeekForm({ week }: { week: StaffWeek }) {
    return (
        <div className="rota-form read-only-schedule">
            <div className="section-heading">
                <span className="requirement-pill sage">
                    {week.overridden
                        ? "Schedule for this week"
                        : "Using recurring hours"}
                </span>
                <span className="field-note">View only</span>
            </div>
            <div className="rota-days">
                {Array.from({ length: 7 }, (_, day) => {
                    const date = addDays(week.weekStart, day);
                    const entries = week.shifts.filter(
                        shift => shift.date === date
                    );
                    return (
                        <div
                            className={`rota-day ${entries.length ? "" : "day-off"}`}
                            key={date}
                        >
                            <div className="rota-day-label">
                                <strong>
                                    {
                                        [
                                            "Monday",
                                            "Tuesday",
                                            "Wednesday",
                                            "Thursday",
                                            "Friday",
                                            "Saturday",
                                            "Sunday"
                                        ][day]
                                    }
                                </strong>
                                <small>{date.slice(5)}</small>
                            </div>
                            <span className="requirement-pill">
                                {entries.length ? "Working" : "Day off"}
                            </span>
                            <div className="read-only-hours">
                                {entries.map((shift, index) => (
                                    <span key={index}>
                                        {shift.startTime.slice(0, 5)}–
                                        {shift.endTime.slice(0, 5)}
                                    </span>
                                ))}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
function WeekForm({
    id,
    week,
    timeZone
}: {
    id: string;
    week: StaffWeek;
    timeZone: string;
}) {
    const client = useQueryClient();
    const [shifts, setShifts] = useState(
        week.shifts.map(shift => ({
            ...shift,
            startTime: shift.startTime.slice(0, 5),
            endTime: shift.endTime.slice(0, 5)
        }))
    );
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const [saved, setSaved] = useState(false);
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        setSaved(false);
        if (shifts.some(shift => shift.endTime <= shift.startTime)) {
            setError("Each shift must end after it starts.");
            return;
        }
        setPending(true);
        try {
            await saveStaffWeek(id, week.weekStart, shifts);
            await refresh();
            setSaved(true);
        } catch (e) {
            setError(
                e instanceof Error ? e.message : "Could not save schedule."
            );
        } finally {
            setPending(false);
        }
    }
    async function refresh() {
        await Promise.all([
            invalidateBookingData(client),
            client.invalidateQueries({
                queryKey: ["staff-week", id, week.weekStart]
            })
        ]);
    }
    async function copyPrevious() {
        setPending(true);
        setError("");
        try {
            const previous = await getStaffWeek(
                id,
                addDays(week.weekStart, -7)
            );
            setShifts(
                copyShiftsToWeek(
                    previous.shifts,
                    previous.weekStart,
                    week.weekStart
                )
            );
            setSaved(false);
        } catch (e) {
            setError(
                e instanceof Error ? e.message : "Could not copy schedule."
            );
        } finally {
            setPending(false);
        }
    }
    async function reset() {
        setPending(true);
        setError("");
        try {
            await resetStaffWeek(id, week.weekStart);
            const inherited = await getStaffWeek(id, week.weekStart);
            setShifts(inherited.shifts);
            await refresh();
            setSaved(true);
        } catch (e) {
            setError(
                e instanceof Error ? e.message : "Could not reset schedule."
            );
        } finally {
            setPending(false);
        }
    }
    function copyMonday() {
        const monday = shifts.filter(shift => shift.date === week.weekStart);
        const rest = shifts.filter(
            shift =>
                ![1, 2, 3, 4].some(
                    day => shift.date === addDays(week.weekStart, day)
                )
        );
        setShifts([
            ...rest,
            ...[1, 2, 3, 4].flatMap(day =>
                monday.map(shift => ({
                    ...shift,
                    date: addDays(week.weekStart, day)
                }))
            )
        ]);
        setSaved(false);
    }
    function update(index: number, changes: Partial<StaffShift>) {
        setShifts(previous =>
            previous.map((shift, i) =>
                i === index ? { ...shift, ...changes } : shift
            )
        );
        setSaved(false);
    }
    if (!canManageWorkspace()) return <ReadOnlyWeekForm week={week} />;
    return (
        <form className="rota-form" onSubmit={submit}>
            <div className="section-heading">
                <span className="requirement-pill sage">
                    {week.overridden
                        ? "Schedule for this week"
                        : "Using recurring hours"}
                </span>
                <div className="form-actions">
                    <button
                        className="button button-secondary"
                        type="button"
                        disabled={pending || !canManageWorkspace()}
                        onClick={copyPrevious}
                    >
                        <Copy size={15} />
                        Copy previous week
                    </button>
                    <button
                        className="button button-secondary"
                        type="button"
                        disabled={pending || !canManageWorkspace()}
                        onClick={copyMonday}
                        data-tooltip="Copy Monday's shifts to Tuesday through Friday. Weekend shifts stay as they are."
                    >
                        Copy Monday to weekdays
                    </button>
                </div>
            </div>
            <fieldset
                disabled={pending || !canManageWorkspace()}
                className="editor-fields rota-days"
            >
                {Array.from({ length: 7 }, (_, day) => {
                    const date = addDays(week.weekStart, day);
                    const entries = shifts
                        .map((shift, index) => ({ shift, index }))
                        .filter(entry => entry.shift.date === date);
                    return (
                        <div
                            className={`rota-day ${entries.length === 0 ? "day-off" : ""}`}
                            key={date}
                        >
                            <div className="rota-day-label">
                                <strong>
                                    {
                                        [
                                            "Monday",
                                            "Tuesday",
                                            "Wednesday",
                                            "Thursday",
                                            "Friday",
                                            "Saturday",
                                            "Sunday"
                                        ][day]
                                    }
                                </strong>
                                <small>{date.slice(5)}</small>
                                <button
                                    className="day-dot-button"
                                    type="button"
                                    aria-label={`Working on ${date}`}
                                    aria-pressed={entries.length > 0}
                                    onClick={() => {
                                        setShifts(previous =>
                                            entries.length === 0
                                                ? [
                                                      ...previous,
                                                      {
                                                          date,
                                                          startTime: "09:00",
                                                          endTime: "17:00"
                                                      }
                                                  ]
                                                : previous.filter(
                                                      shift =>
                                                          shift.date !== date
                                                  )
                                        );
                                        setSaved(false);
                                    }}
                                >
                                    <span>
                                        {entries.length > 0 && (
                                            <Check size={14} />
                                        )}
                                    </span>
                                    {entries.length ? "Working" : "Day off"}
                                </button>
                            </div>
                            <div className="rota-shifts">
                                {entries.map(({ shift, index }) => (
                                    <div className="rota-shift" key={index}>
                                        <label className="form-field">
                                            Start
                                            <DateTimeInput
                                                className="input"
                                                type="time"
                                                required
                                                value={shift.startTime.slice(
                                                    0,
                                                    5
                                                )}
                                                onChange={e =>
                                                    update(index, {
                                                        startTime:
                                                            e.target.value
                                                    })
                                                }
                                            />
                                        </label>
                                        <span className="field-note">to</span>
                                        <label className="form-field">
                                            End
                                            <DateTimeInput
                                                className="input"
                                                type="time"
                                                required
                                                value={shift.endTime.slice(
                                                    0,
                                                    5
                                                )}
                                                onChange={e =>
                                                    update(index, {
                                                        endTime: e.target.value
                                                    })
                                                }
                                            />
                                        </label>
                                        <button
                                            className="button button-secondary icon-action"
                                            type="button"
                                            aria-label={`Remove shift on ${date}`}
                                            data-tooltip="Remove this shift"
                                            onClick={() => {
                                                setShifts(previous =>
                                                    previous.filter(
                                                        (_, i) => i !== index
                                                    )
                                                );
                                                setSaved(false);
                                            }}
                                        >
                                            <Trash2 size={15} />
                                        </button>
                                    </div>
                                ))}
                                {entries.length > 0 && (
                                    <button
                                        className="text-action"
                                        type="button"
                                        disabled={shifts.length >= 28}
                                        onClick={() => {
                                            setShifts(previous => [
                                                ...previous,
                                                {
                                                    date,
                                                    startTime: "18:00",
                                                    endTime: "20:00"
                                                }
                                            ]);
                                            setSaved(false);
                                        }}
                                    >
                                        <Plus size={13} />
                                        Add split shift
                                    </button>
                                )}
                            </div>
                        </div>
                    );
                })}
            </fieldset>
            <p className="field-note">
                Times are in {timeZone}. Saving replaces only this week. Time
                off and location closures still take precedence.
            </p>
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
            {saved && (
                <p className="booking-summary" role="status">
                    Schedule saved.
                </p>
            )}
            <div className="form-actions">
                {week.overridden && (
                    <button
                        className="button button-secondary"
                        type="button"
                        disabled={pending || !canManageWorkspace()}
                        onClick={reset}
                    >
                        Use recurring hours
                    </button>
                )}
                <button
                    className="button button-primary"
                    disabled={pending || !canManageWorkspace()}
                >
                    <Save size={16} />
                    {pending ? "Saving…" : "Save this week"}
                </button>
            </div>
        </form>
    );
}
export default function StaffWeekEditor({
    staffId,
    staffName,
    timeZone,
    weekStart,
    onWeekChange,
    nextWeek
}: {
    staffId: string;
    staffName: string;
    timeZone: string;
    weekStart: string;
    onWeekChange: (week: string) => void;
    nextWeek: string;
}) {
    const query = useQuery({
        queryKey: ["staff-week", staffId, weekStart],
        queryFn: () => getStaffWeek(staffId, weekStart)
    });
    return (
        <section className="card management-panel tinted-panel sage-panel">
            <div className="section-heading">
                <div>
                    <p className="page-eyebrow">
                        Weekly rota · {staffName}
                        {weekStart === nextWeek ? " · Next-week planning" : ""}
                    </p>
                    <h2>
                        {formatDate(`${weekStart}T12:00:00Z`, timeZone)} —{" "}
                        {formatDate(
                            `${addDays(weekStart, 6)}T12:00:00Z`,
                            timeZone
                        )}
                    </h2>
                </div>
                <div className="form-actions">
                    <button
                        className="button button-secondary icon-action"
                        aria-label="Previous schedule week"
                        onClick={() => onWeekChange(addDays(weekStart, -7))}
                    >
                        <ChevronLeft size={17} />
                    </button>
                    <button
                        className="button button-secondary"
                        onClick={() => onWeekChange(nextWeek)}
                    >
                        Next week
                    </button>
                    <button
                        className="button button-secondary icon-action"
                        aria-label="Next schedule week"
                        onClick={() => onWeekChange(addDays(weekStart, 7))}
                    >
                        <ChevronRight size={17} />
                    </button>
                </div>
            </div>
            {query.isPending ? (
                <p>Loading schedule…</p>
            ) : query.error ? (
                <p className="form-error" role="alert">
                    {query.error.message}
                </p>
            ) : (
                <WeekForm
                    key={`${staffId}:${weekStart}`}
                    id={staffId}
                    week={query.data}
                    timeZone={timeZone}
                />
            )}
        </section>
    );
}
