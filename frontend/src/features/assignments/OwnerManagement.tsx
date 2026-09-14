import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
    createAssignment,
    updateAssignment,
    setAssignmentActive
} from "./assignmentApi";
import { invalidateBookingData } from "./invalidateBookingData";
import type { Assignment, AssignmentKind } from "./assignmentTypes";
interface Props {
    kind: AssignmentKind;
    owner?: Assignment;
    onCreated: (id: string) => void;
}
export default function OwnerManagement({ kind, owner, onCreated }: Props) {
    const client = useQueryClient();
    const [mode, setMode] = useState<"create" | "edit" | null>(null);
    const [name, setName] = useState("");
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        if (kind === "resources") return;
        setPending(true);
        setError("");
        try {
            const saved =
                mode === "edit" && owner
                    ? await updateAssignment(
                          kind,
                          owner.id,
                          name.trim(),
                          owner.active
                      )
                    : await createAssignment(kind, name.trim());
            await invalidateBookingData(client);
            onCreated(saved.id);
            setMode(null);
        } catch (e) {
            setError(
                e instanceof Error ? e.message : "Could not save assignment."
            );
        } finally {
            setPending(false);
        }
    }
    async function toggleActive() {
        if (!owner) return;
        setPending(true);
        setError("");
        try {
            await setAssignmentActive(kind, owner.id, !owner.active);
            await invalidateBookingData(client);
        } catch (e) {
            setError(
                e instanceof Error ? e.message : "Could not update status."
            );
        } finally {
            setPending(false);
        }
    }
    return (
        <div>
            <div className="section-heading">
                <span className="field-note">
                    {owner
                        ? owner.active
                            ? "Active"
                            : "Inactive — bookings are unavailable"
                        : "Select an owner to configure availability."}
                </span>
                <div className="form-actions">
                    {kind === "resources" ? (
                        <Link
                            className="button button-secondary"
                            to="/resources"
                        >
                            Manage resources
                        </Link>
                    ) : (
                        <button
                            className="button button-secondary"
                            onClick={() => {
                                setName("");
                                setError("");
                                setMode("create");
                            }}
                        >
                            Add {kind === "staff" ? "staff member" : "location"}
                        </button>
                    )}
                    {owner && kind !== "resources" && (
                        <button
                            className="button button-secondary"
                            onClick={() => {
                                setName(owner.name);
                                setError("");
                                setMode("edit");
                            }}
                        >
                            Rename
                        </button>
                    )}
                    {owner && (
                        <button
                            className={`button ${owner.active ? "button-danger" : "button-secondary"}`}
                            disabled={pending}
                            onClick={toggleActive}
                        >
                            {owner.active ? "Deactivate" : "Activate"}
                        </button>
                    )}
                </div>
            </div>
            {mode && (
                <form
                    className="form-grid"
                    onSubmit={submit}
                    style={{ marginTop: 16 }}
                >
                    <label className="form-field">
                        Name
                        <input
                            className="input"
                            autoFocus
                            required
                            maxLength={150}
                            value={name}
                            onChange={e => setName(e.target.value)}
                        />
                    </label>
                    <div className="form-actions">
                        <button
                            className="button button-secondary"
                            type="button"
                            onClick={() => setMode(null)}
                        >
                            Cancel
                        </button>
                        <button
                            className="button button-primary"
                            disabled={pending}
                        >
                            {pending ? "Saving…" : "Save"}
                        </button>
                    </div>
                </form>
            )}
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
        </div>
    );
}
