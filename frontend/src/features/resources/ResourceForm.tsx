import SearchSelect from "../../components/SearchSelect";
import { useState, type FormEvent } from "react";
import { Package } from "lucide-react";
import type {
    BookableResource,
    ResourceRequest,
    ResourceType
} from "./resourceTypes";
interface Props {
    resource?: BookableResource;
    onSubmit: (values: ResourceRequest) => Promise<void>;
    onCancel: () => void;
}
export default function ResourceForm({ resource, onSubmit, onCancel }: Props) {
    const [values, setValues] = useState<ResourceRequest>({
        name: resource?.name ?? "",
        description: resource?.description ?? "",
        type: resource?.type ?? "EQUIPMENT",
        active: resource?.active ?? true
    });
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    async function submit(event: FormEvent) {
        event.preventDefault();
        setPending(true);
        setError("");
        try {
            await onSubmit({ ...values, name: values.name.trim() });
        } catch (e) {
            setError(
                e instanceof Error ? e.message : "Could not save resource."
            );
        } finally {
            setPending(false);
        }
    }
    return (
        <form
            className="card management-form tinted-panel sage-panel"
            onSubmit={submit}
        >
            <div className="section-heading">
                <h2>{resource ? "Edit resource" : "Add resource"}</h2>
                <Package size={24} />
            </div>
            <fieldset className="editor-fields" disabled={pending}>
                <div className="form-grid">
                    <label className="form-field">
                        Resource name
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
                        Type
                        <SearchSelect
                            searchable={false}
                            disabled={pending}
                            value={values.type}
                            onChange={value =>
                                setValues({
                                    ...values,
                                    type: value as ResourceType
                                })
                            }
                            ariaLabel="Resource type"
                            options={[
                                { value: "EQUIPMENT", label: "Equipment" },
                                { value: "VEHICLE", label: "Vehicle" },
                                { value: "OTHER", label: "Other" }
                            ]}
                        />
                    </label>
                </div>
                <label className="check-row">
                    <input
                        type="checkbox"
                        checked={values.active}
                        onChange={e =>
                            setValues({ ...values, active: e.target.checked })
                        }
                    />
                    Resource active
                </label>
                <label className="form-field">
                    Description
                    <textarea
                        className="textarea"
                        maxLength={1000}
                        value={values.description}
                        onChange={e =>
                            setValues({
                                ...values,
                                description: e.target.value
                            })
                        }
                    />
                </label>
                <p className="field-note">
                    Resources are physical things used in a booking. Manage
                    people in Staff and places in Locations.
                </p>
            </fieldset>
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
            <div className="form-actions">
                <button
                    className="button button-secondary"
                    type="button"
                    disabled={pending}
                    onClick={onCancel}
                >
                    Cancel
                </button>
                <button className="button button-primary" disabled={pending}>
                    {pending ? "Saving…" : "Save resource"}
                </button>
            </div>
        </form>
    );
}
