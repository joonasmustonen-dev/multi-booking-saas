import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ShieldCheck, Save, Eye } from "lucide-react";
import { apiFetch } from "../../api/apiClient";
import { invalidateBookingData } from "../assignments/invalidateBookingData";

interface Policy {
    customerRetentionDays: number;
    notesRetentionDays: number;
    staffRetentionDays: number;
    auditRetentionDays: number;
    scheduledRetention: boolean;
}
interface Preview {
    customers: number;
    notes: number;
    staffContacts: number;
    auditEvents: number;
    token: string;
    customerBatchLimit: number;
}
interface AuditEvent {
    id: string;
    occurredAt: string;
    actorId: string;
    action: string;
    recordId: string | null;
    outcome: number;
}

function PolicyEditor({ policy }: { policy: Policy }) {
    const client = useQueryClient();
    const [values, setValues] = useState(policy);
    const [pending, setPending] = useState(false);
    const [preview, setPreview] = useState<Preview | null>(null);
    const [confirmed, setConfirmed] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const dirty = JSON.stringify(values) !== JSON.stringify(policy);

    async function perform(action: () => Promise<void>) {
        setPending(true);
        setError("");
        setMessage("");
        try {
            await action();
        } catch (failure) {
            setError(
                failure instanceof Error
                    ? failure.message
                    : "Could not update privacy settings."
            );
        } finally {
            setPending(false);
        }
    }

    const fields = [
        ["customerRetentionDays", "Inactive customer contact data"],
        ["notesRetentionDays", "Past appointment notes"],
        ["staffRetentionDays", "Archived staff email and phone"],
        ["auditRetentionDays", "Security audit events"]
    ] as const;

    return (
        <section className="card management-panel tinted-panel sage-panel">
            <h2>
                <ShieldCheck size={20} /> Data retention
            </h2>
            <p className="field-note">
                Set periods based on your documented purposes and legal
                obligations. Zero disables cleanup for that category. Legal
                holds protect customer data. Customer cleanup removes contact
                details, preferences and notes while retaining pseudonymous
                booking facts.
            </p>
            <fieldset disabled={pending} className="editor-fields">
                <div className="form-grid">
                    {fields.map(([key, label]) => (
                        <label className="form-field" key={key}>
                            {label} (days)
                            <input
                                className="input"
                                type="number"
                                min={0}
                                max={36500}
                                step={1}
                                value={values[key]}
                                onChange={event => {
                                    setValues({
                                        ...values,
                                        [key]: Number(event.target.value)
                                    });
                                    setPreview(null);
                                    setConfirmed(false);
                                }}
                            />
                        </label>
                    ))}
                </div>
                <label className="checkbox-field">
                    <input
                        type="checkbox"
                        checked={values.scheduledRetention}
                        onChange={event => {
                            setValues({
                                ...values,
                                scheduledRetention: event.target.checked
                            });
                            setPreview(null);
                        }}
                    />{" "}
                    Automatically apply the saved policy daily
                </label>
                <p className="field-note">
                    Automatic cleanup uses bounded batches. Historical booking
                    facts and staff names still require a separate retention
                    review.
                </p>
                <div className="form-actions">
                    <button
                        type="button"
                        className="button button-primary"
                        onClick={() =>
                            void perform(async () => {
                                if (
                                    fields.some(
                                        ([key]) =>
                                            !Number.isInteger(values[key]) ||
                                            values[key] < 0 ||
                                            values[key] > 36500
                                    )
                                ) {
                                    throw new Error(
                                        "Retention periods must be whole numbers between 0 and 36,500 days."
                                    );
                                }
                                const saved = await apiFetch<Policy>(
                                    "/api/v1/privacy/policy",
                                    {
                                        method: "PUT",
                                        body: JSON.stringify(values)
                                    }
                                );
                                client.setQueryData(["privacy-policy"], saved);
                                setPreview(null);
                                setConfirmed(false);
                                setMessage("Retention policy saved.");
                            })
                        }
                    >
                        <Save size={16} /> Save policy
                    </button>
                    <button
                        type="button"
                        className="button button-secondary"
                        disabled={dirty}
                        onClick={() =>
                            void perform(async () => {
                                setPreview(
                                    await apiFetch<Preview>(
                                        "/api/v1/privacy/retention"
                                    )
                                );
                                setConfirmed(false);
                            })
                        }
                    >
                        <Eye size={16} /> Preview cleanup
                    </button>
                </div>
                {dirty && (
                    <p className="field-note">
                        Save the policy before previewing cleanup.
                    </p>
                )}
                {preview && (
                    <div className="booking-summary">
                        <strong>Next cleanup batch</strong>
                        <p>
                            {preview.customers} customers · {preview.notes}{" "}
                            notes · {preview.staffContacts} staff contacts ·{" "}
                            {preview.auditEvents} audit events
                        </p>
                        <p className="field-note">
                            Up to {preview.customerBatchLimit} customers and
                            1,000 items per other category per batch.
                        </p>
                        <label className="checkbox-field">
                            <input
                                type="checkbox"
                                checked={confirmed}
                                onChange={event =>
                                    setConfirmed(event.target.checked)
                                }
                            />{" "}
                            I have reviewed this cleanup and the applicable
                            retention obligations.
                        </label>
                        <button
                            type="button"
                            className="button button-danger"
                            disabled={
                                !confirmed ||
                                dirty ||
                                preview.customers +
                                    preview.notes +
                                    preview.staffContacts +
                                    preview.auditEvents ===
                                    0
                            }
                            onClick={() =>
                                void perform(async () => {
                                    await apiFetch(
                                        "/api/v1/privacy/retention/apply",
                                        {
                                            method: "POST",
                                            body: JSON.stringify({
                                                token: preview.token,
                                                confirmed
                                            })
                                        }
                                    );
                                    setPreview(null);
                                    setConfirmed(false);
                                    await invalidateBookingData(client);
                                    await client.invalidateQueries({
                                        queryKey: ["privacy-audit"]
                                    });
                                    setMessage("Cleanup batch completed.");
                                })
                            }
                        >
                            Apply cleanup
                        </button>
                    </div>
                )}
            </fieldset>
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
            {message && (
                <p className="booking-summary" role="status">
                    {message}
                </p>
            )}
        </section>
    );
}

export default function PrivacySettings() {
    const policy = useQuery({
        queryKey: ["privacy-policy"],
        queryFn: () => apiFetch<Policy>("/api/v1/privacy/policy")
    });
    const audit = useQuery({
        queryKey: ["privacy-audit"],
        queryFn: () => apiFetch<AuditEvent[]>("/api/v1/privacy/audit")
    });
    return (
        <div className="privacy-settings">
            {policy.isPending && <p>Loading privacy settings…</p>}
            {policy.error && (
                <p className="form-error" role="alert">
                    {policy.error.message}
                </p>
            )}
            {policy.data && <PolicyEditor policy={policy.data} />}
            <section className="card management-panel">
                <div className="section-heading">
                    <h2>Recent security activity</h2>
                    <button
                        type="button"
                        className="button button-secondary"
                        onClick={() => void audit.refetch()}
                    >
                        Refresh
                    </button>
                </div>
                <p className="field-note">
                    Records operation, user identifier and outcome without
                    copying contact details or notes.
                </p>
                {audit.error && (
                    <p className="form-error" role="alert">
                        {audit.error.message}
                    </p>
                )}
                <div className="privacy-audit-list">
                    {audit.data?.map(event => (
                        <article key={event.id}>
                            <div>
                                <strong>
                                    {event.action.includes("export")
                                        ? "Customer data export"
                                        : event.action.includes("erase")
                                          ? "Customer erasure"
                                          : event.action.includes("retention")
                                            ? "Retention cleanup"
                                            : `${event.action.split(":")[1]} change`}
                                </strong>
                                <small>
                                    {new Date(
                                        event.occurredAt
                                    ).toLocaleString()}{" "}
                                    · User {event.actorId.slice(0, 8)}
                                </small>
                            </div>
                            <span
                                className={`requirement-pill ${event.outcome < 400 ? "sage" : "muted"}`}
                            >
                                {event.outcome < 400 ? "Succeeded" : "Rejected"}
                            </span>
                        </article>
                    ))}
                </div>
                {audit.data?.length === 0 && (
                    <p className="field-note">
                        No security activity recorded yet.
                    </p>
                )}
            </section>
        </div>
    );
}
