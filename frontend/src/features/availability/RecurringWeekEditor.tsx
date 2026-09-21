import DateTimeInput from "../../components/DateTimeInput";

import { canManageWorkspace } from "../../auth/permissions";

import { useState, type FormEvent } from "react";

import { useQueryClient } from "@tanstack/react-query";

import { Check, Copy, Plus, Save, Trash2 } from "lucide-react";

import type { AssignmentKind } from "../assignments/assignmentTypes";

import type { AvailabilityRule } from "./availabilityTypes";

import { replaceAvailabilityRules } from "./availabilityApi";

import { invalidateBookingData } from "../assignments/invalidateBookingData";

import {
    weekDays,
    toggleRecurringDay,
    recurringWeekdaysPreset,
    validateRecurringDraft,
    type RecurringDraft
} from "./recurringDraft";

function ReadOnlyRecurringWeek({ rules }: { rules: AvailabilityRule[] }) {
    return (
        <div className="recurring-week read-only-schedule">
            <div>
                <h3>Regular weekly hours</h3>
                <p className="field-note">View-only schedule</p>
            </div>
            <div className="recurring-rota-days">
                {weekDays.map(day => {
                    const entries = rules.filter(
                        rule => rule.dayOfWeek === day && rule.active
                    );
                    return (
                        <article
                            className={`rota-day ${entries.length ? "" : "day-off"}`}
                            key={day}
                        >
                            <strong>
                                {day.charAt(0) + day.slice(1).toLowerCase()}
                            </strong>
                            <span className="requirement-pill">
                                {entries.length ? "Open" : "Closed"}
                            </span>
                            <div className="read-only-hours">
                                {entries.length
                                    ? entries.map((rule, index) => (
                                          <span key={index}>
                                              {rule.startTime.slice(0, 5)}–
                                              {rule.endTime.slice(0, 5)}
                                          </span>
                                      ))
                                    : null}
                            </div>
                        </article>
                    );
                })}
            </div>
        </div>
    );
}

export default function RecurringWeekEditor({
    kind,

    ownerId,

    initialRules
}: {
    kind: AssignmentKind;

    ownerId: string;

    initialRules: AvailabilityRule[];
}) {
    const client = useQueryClient();

    const [rules, setRules] = useState<RecurringDraft[]>(
        initialRules.map(rule => ({
            dayOfWeek: rule.dayOfWeek,

            startTime: rule.startTime.slice(0, 5),

            endTime: rule.endTime.slice(0, 5),

            active: rule.active
        }))
    );

    const [pending, setPending] = useState(false);

    const [error, setError] = useState("");

    const [saved, setSaved] = useState(false);

    function change(next: RecurringDraft[]) {
        setRules(next);

        setSaved(false);

        setError("");
    }

    function update(index: number, changes: Partial<RecurringDraft>) {
        change(
            rules.map((rule, i) =>
                i === index ? { ...rule, ...changes } : rule
            )
        );
    }

    function copyMonday() {
        const monday = rules.filter(rule => rule.dayOfWeek === "MONDAY");

        change([
            ...rules.filter(
                rule => !weekDays.slice(1, 5).includes(rule.dayOfWeek)
            ),

            ...weekDays

                .slice(1, 5)

                .flatMap(dayOfWeek =>
                    monday.map(rule => ({ ...rule, dayOfWeek }))
                )
        ]);
    }

    async function save(event: FormEvent) {
        event.preventDefault();

        if (pending) return;

        const message = validateRecurringDraft(rules);

        if (message) {
            setError(message);

            return;
        }

        setPending(true);

        setError("");

        try {
            await replaceAvailabilityRules(kind, ownerId, rules);

            await invalidateBookingData(client);

            setSaved(true);
        } catch (e) {
            setError(
                e instanceof Error
                    ? e.message
                    : "Could not save recurring hours."
            );
        } finally {
            setPending(false);
        }
    }

    if (!canManageWorkspace()) {
        return <ReadOnlyRecurringWeek rules={initialRules} />;
    }

    return (
        <form className="recurring-week" onSubmit={save}>
            <div className="recurring-presets">
                <div>
                    <h3>Regular weekly hours</h3>
                    <p className="field-note">
                        Set opening hours for each day. Exceptions still take
                        precedence.
                    </p>
                </div>

                <button
                    type="button"
                    className="button button-secondary"
                    disabled={pending || !canManageWorkspace()}
                    onClick={() => change(recurringWeekdaysPreset())}
                >
                    Weekdays 09–17
                </button>

                <button
                    type="button"
                    className="button button-secondary"
                    disabled={pending || !canManageWorkspace()}
                    onClick={copyMonday}
                >
                    <Copy size={14} /> Copy Monday to weekdays
                </button>
            </div>

            <fieldset
                className="editor-fields recurring-rota-days"
                disabled={pending || !canManageWorkspace()}
            >
                {weekDays.map(day => {
                    const entries = rules
                        .map((rule, index) => ({ rule, index }))
                        .filter(
                            ({ rule }) => rule.dayOfWeek === day && rule.active
                        );

                    const working = entries.length > 0;

                    return (
                        <article
                            className={`rota-day ${working ? "" : "day-off"}`}
                            key={day}
                        >
                            <strong>
                                {day.charAt(0) + day.slice(1).toLowerCase()}
                            </strong>

                            <button
                                type="button"
                                className="day-dot-button"
                                aria-label={`${day.charAt(0) + day.slice(1).toLowerCase()} recurring hours`}
                                aria-pressed={working}
                                onClick={() =>
                                    change(
                                        toggleRecurringDay(rules, day, !working)
                                    )
                                }
                            >
                                <span>{working && <Check size={14} />}</span>
                                {working ? "Open" : "Closed"}
                            </button>

                            {entries.map(({ rule, index }) => (
                                <div
                                    className="recurring-rota-shift"
                                    key={index}
                                >
                                    <label className="form-field">
                                        Start
                                        <DateTimeInput
                                            className="input"
                                            type="time"
                                            required
                                            value={rule.startTime}

                                            onChange={event =>
                                                update(index, {
                                                    startTime:
                                                        event.target.value
                                                })
                                            }
                                        />
                                    </label>

                                    <label className="form-field">
                                        End
                                        <DateTimeInput
                                            className="input"
                                            type="time"
                                            required
                                            value={rule.endTime}

                                            onChange={event =>
                                                update(index, {
                                                    endTime: event.target.value
                                                })
                                            }
                                        />
                                    </label>

                                    <button
                                        type="button"
                                        className="button button-secondary recurring-remove"
                                        aria-label={`Remove ${day.toLowerCase()} time period`}

                                        onClick={() =>
                                            change(
                                                rules.filter(
                                                    (_, item) => item !== index
                                                )
                                            )
                                        }
                                    >
                                        <Trash2 size={14} />
                                    </button>
                                </div>
                            ))}

                            {working && (
                                <button
                                    type="button"
                                    className="text-action"
                                    disabled={rules.length >= 28}
                                    onClick={() =>
                                        change([
                                            ...rules,
                                            {
                                                dayOfWeek: day,
                                                startTime: "18:00",
                                                endTime: "20:00",
                                                active: true
                                            }
                                        ])
                                    }
                                >
                                    <Plus size={13} /> Add split hours
                                </button>
                            )}
                        </article>
                    );
                })}
            </fieldset>

            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}

            {saved && (
                <p className="field-note" role="status">
                    Recurring hours saved.
                </p>
            )}

            <div className="form-actions">
                <button
                    className="button button-primary"
                    disabled={pending || !canManageWorkspace()}
                >
                    <Save size={16} />
                    {pending ? "Saving…" : "Save recurring hours"}
                </button>
            </div>
        </form>
    );
}
