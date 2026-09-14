import { useState } from "react";
import { Download, ShieldCheck, Trash2 } from "lucide-react";
import { apiFetch } from "../../api/apiClient";
import type { Customer } from "./customerTypes";
import type { AppointmentResponse } from "../appointments/appointmentTypes";

interface ExportPage {
    customer: Customer;
    lastActivityAt: string;
    appointments: AppointmentResponse[];
    totalPages: number;
    totalAppointments: number;
    revision: string;
}

export default function CustomerPrivacyPanel({
    customer,
    onChanged,
    onErased
}: {
    customer: Customer;
    onChanged: () => Promise<void>;
    onErased: () => void;
}) {
    const [restricted, setRestricted] = useState(
        !!customer.processingRestricted
    );
    const [hold, setHold] = useState(!!customer.legalHold);
    const [mode, setMode] = useState<"CONTACT_DETAILS" | "ALL_DATA">(
        "CONTACT_DETAILS"
    );
    const [showErase, setShowErase] = useState(false);
    const [confirmed, setConfirmed] = useState(false);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");

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
                    : "Could not complete this operation."
            );
        } finally {
            setPending(false);
        }
    }

    async function download() {
        const first = await apiFetch<ExportPage>(
            `/api/v1/customers/${customer.id}/export`
        );
        if (first.totalPages > 100)
            throw new Error(
                "This export is too large for a browser download. Use the paginated administrator export API."
            );
        const appointments = [...first.appointments];
        for (let page = 1; page < first.totalPages; page++) {
            const next = await apiFetch<ExportPage>(
                `/api/v1/customers/${customer.id}/export?page=${page}`
            );
            if (next.revision !== first.revision)
                throw new Error(
                    "Customer data changed during export. Please export again."
                );
            appointments.push(...next.appointments);
        }
        if (
            appointments.length !== first.totalAppointments ||
            new Set(appointments.map(booking => booking.id)).size !==
                appointments.length
        ) {
            throw new Error(
                "Booking history changed during export. Please export again."
            );
        }
        const url = URL.createObjectURL(
            new Blob(
                [
                    JSON.stringify(
                        {
                            exportedAt: new Date().toISOString(),
                            customer: first.customer,
                            lastActivityAt: first.lastActivityAt,
                            appointments
                        },
                        null,
                        4
                    )
                ],
                { type: "application/json" }
            )
        );
        const link = document.createElement("a");
        link.href = url;
        link.download = `customer-${customer.id}.json`;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        setMessage("Export downloaded. Handle the file as personal data.");
    }

    return (
        <section className="card tinted-panel sage-panel customer-privacy-panel">
            <h3>
                <ShieldCheck size={18} /> Customer data controls
            </h3>
            <p className="field-note">
                Administrator controls. Verify the requester’s identity and
                review applicable retention obligations before exporting or
                erasing data.
            </p>
            <fieldset disabled={pending} className="editor-fields">
                <label className="checkbox-field">
                    <input
                        type="checkbox"
                        checked={restricted}
                        disabled={!!customer.erasedAt}
                        onChange={event => setRestricted(event.target.checked)}
                    />{" "}
                    Restrict processing
                </label>
                <p className="field-note">
                    Pauses new bookings, rescheduling and recommendations. Staff
                    cannot access customer contact details or appointment notes
                    while restricted.
                </p>
                <label className="checkbox-field">
                    <input
                        type="checkbox"
                        checked={hold}
                        onChange={event => setHold(event.target.checked)}
                    />{" "}
                    Legal hold
                </label>
                <p className="field-note">
                    Protects this customer’s data and notes from erasure and
                    retention cleanup.
                </p>
                <div className="form-actions">
                    <button
                        type="button"
                        className="button button-secondary"
                        onClick={() =>
                            void perform(async () => {
                                await apiFetch(
                                    `/api/v1/customers/${customer.id}/privacy`,
                                    {
                                        method: "PATCH",
                                        body: JSON.stringify({
                                            processingRestricted: restricted,
                                            legalHold: hold
                                        })
                                    }
                                );
                                await onChanged();
                                setMessage("Customer data controls saved.");
                            })
                        }
                    >
                        Save controls
                    </button>
                    <button
                        type="button"
                        className="button button-secondary"
                        onClick={() => void perform(download)}
                    >
                        <Download size={16} /> Export data
                    </button>
                    <button
                        type="button"
                        className="button button-danger"
                        disabled={hold || !!customer.legalHold}
                        onClick={() => {
                            setShowErase(!showErase);
                            setConfirmed(false);
                        }}
                    >
                        <Trash2 size={16} /> Erase data…
                    </button>
                </div>
                {showErase && (
                    <div className="customer-delete-section">
                        <label className="form-field">
                            Erasure scope
                            <select
                                className="input"
                                value={mode}
                                onChange={event => {
                                    setMode(event.target.value as typeof mode);
                                    setConfirmed(false);
                                }}
                            >
                                <option value="CONTACT_DETAILS">
                                    Contact details, preferences and notes
                                </option>
                                <option value="ALL_DATA">
                                    Customer and all booking history
                                </option>
                            </select>
                        </label>
                        <p className="field-note">
                            Both options cancel pending and confirmed bookings.
                            Keeping booking facts retains a pseudonymous record;
                            it does not guarantee anonymity. Full erasure
                            permanently removes booking history.
                        </p>
                        <label className="checkbox-field">
                            <input
                                type="checkbox"
                                checked={confirmed}
                                onChange={event =>
                                    setConfirmed(event.target.checked)
                                }
                            />{" "}
                            I have reviewed retention obligations and confirm
                            this irreversible action.
                        </label>
                        <div className="form-actions">
                            <button
                                type="button"
                                className="button button-danger"
                                disabled={
                                    !confirmed || hold || !!customer.legalHold
                                }
                                onClick={() =>
                                    void perform(async () => {
                                        await apiFetch(
                                            `/api/v1/customers/${customer.id}/erase`,
                                            {
                                                method: "POST",
                                                body: JSON.stringify({
                                                    mode,
                                                    confirmed
                                                })
                                            }
                                        );
                                        await onChanged();
                                        onErased();
                                    })
                                }
                            >
                                Confirm erasure
                            </button>
                            <button
                                type="button"
                                className="button button-secondary"
                                onClick={() => setShowErase(false)}
                            >
                                Cancel
                            </button>
                        </div>
                    </div>
                )}
            </fieldset>
            {pending && (
                <p role="status" className="field-note">
                    Working…
                </p>
            )}
            {error && (
                <p role="alert" className="form-error">
                    {error}
                </p>
            )}
            {message && (
                <p role="status" className="booking-summary">
                    {message}
                </p>
            )}
        </section>
    );
}
