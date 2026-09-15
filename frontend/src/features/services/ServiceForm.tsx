import { entityLabel } from "../assignments/entityLabels";
import SearchSelect from "../../components/SearchSelect";
import { useState, type FormEvent } from "react";
import type { AssignmentCatalogs } from "../assignments/assignmentTypes";
import type {
    AssignmentRequirement,
    ServiceOffering,
    UpdateServiceRequest
} from "./serviceTypes";
export type ServiceFormValues = UpdateServiceRequest;
interface Props {
    service?: ServiceOffering;
    catalogs: AssignmentCatalogs;
    onSubmit: (values: ServiceFormValues) => Promise<void>;
    onCancel: () => void;
}
import { useTenantSettings } from "../settings/useTenantSettings";
export default function ServiceForm({
    service,
    catalogs,
    onSubmit,
    onCancel
}: Props) {
    const settings = useTenantSettings();
    const [values, setValues] = useState<ServiceFormValues>({
        name: service?.name ?? "",
        description: service?.description ?? "",
        durationMinutes: service?.durationMinutes ?? 60,
        price: service?.price ?? null,
        currency: service?.currency ?? settings.data?.defaultCurrency ?? "EUR",
        active: service?.active ?? true,
        staffIds: service?.staffIds ?? [],
        locationIds: service?.locationIds ?? [],
        resourceIds: service?.resourceIds ?? [],
        staffRequirement: service?.staffRequirement ?? "REQUIRED",
        locationRequirement: service?.locationRequirement ?? "REQUIRED",
        resourceRequirement: service?.resourceRequirement ?? "FORBIDDEN"
    });
    const [searches, setSearches] = useState({
        staff: "",
        locations: "",
        resources: ""
    });
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const categories = [
        {
            label: "Staff",
            kind: "staff",
            ids: "staffIds",
            requirement: "staffRequirement"
        },
        {
            label: "Locations",
            kind: "locations",
            ids: "locationIds",
            requirement: "locationRequirement"
        },
        {
            label: "Resources",
            kind: "resources",
            ids: "resourceIds",
            requirement: "resourceRequirement"
        }
    ] as const;
    async function submit(event: FormEvent) {
        event.preventDefault();
        setError("");
        if (!values.name.trim()) {
            setError("Enter a service name.");
            return;
        }
        if (!categories.some(c => values[c.requirement] === "REQUIRED")) {
            setError("Require at least one assignment category.");
            return;
        }
        for (const category of categories) {
            const selected = values[category.ids];
            if (
                values[category.requirement] === "REQUIRED" &&
                selected.length === 0
            ) {
                setError(`Select eligible ${category.label.toLowerCase()}.`);
                return;
            }
            if (
                selected.some(
                    id =>
                        !catalogs[category.kind].find(item => item.id === id)
                            ?.active
                )
            ) {
                setError(
                    `Remove inactive ${category.label.toLowerCase()} before saving.`
                );
                return;
            }
        }
        setSubmitting(true);
        try {
            await onSubmit({
                ...values,
                name: values.name.trim(),
                description: values.description?.trim() || null,
                currency: values.currency?.trim().toUpperCase() || null
            });
        } catch (e) {
            setError(
                e instanceof Error ? e.message : "Could not save service."
            );
        } finally {
            setSubmitting(false);
        }
    }
    return (
        <form
            className="card management-form tinted-panel service-editor"
            onSubmit={submit}
        >
            <div className="section-heading">
                <h3>{service ? "Edit service" : "New service"}</h3>
                <p className="field-note">
                    Define what each booking needs and who or what can fulfil
                    it.
                </p>
            </div>
            <div className="form-grid">
                <label className="form-field">
                    Name
                    <input
                        className="input"
                        required
                        maxLength={200}
                        value={values.name}
                        onChange={e =>
                            setValues({ ...values, name: e.target.value })
                        }
                    />
                </label>
                <label className="form-field">
                    Duration (minutes)
                    <input
                        className="input"
                        required
                        type="number"
                        min="1"
                        max="1440"
                        value={values.durationMinutes}
                        onChange={e =>
                            setValues({
                                ...values,
                                durationMinutes: Number(e.target.value)
                            })
                        }
                    />
                </label>
                <label className="form-field">
                    Price
                    <input
                        className="input"
                        type="number"
                        min="0"
                        max="9999999999.99"
                        step="0.01"
                        value={values.price ?? ""}
                        onChange={e =>
                            setValues({
                                ...values,
                                price:
                                    e.target.value === ""
                                        ? null
                                        : Number(e.target.value)
                            })
                        }
                    />
                </label>
                <label className="form-field">
                    Currency
                    <input
                        className="input"
                        maxLength={3}
                        pattern="[A-Z]{3}"
                        value={values.currency ?? ""}
                        onChange={e =>
                            setValues({
                                ...values,
                                currency: e.target.value.toUpperCase()
                            })
                        }
                    />
                </label>
            </div>
            <label className="form-field">
                Description
                <textarea
                    className="textarea"
                    maxLength={2000}
                    value={values.description ?? ""}
                    onChange={e =>
                        setValues({ ...values, description: e.target.value })
                    }
                />
            </label>
            <div className="assignment-grid">
                {categories.map(category => (
                    <fieldset className="assignment-panel" key={category.kind}>
                        <legend>{category.label}</legend>
                        <label className="form-field">
                            Booking requirement
                            <SearchSelect
                                searchable={false}
                                value={values[category.requirement]}
                                ariaLabel={`${category.label} booking requirement`}
                                onChange={value => {
                                    const requirement =
                                        value as AssignmentRequirement;
                                    setValues({
                                        ...values,
                                        [category.requirement]: requirement,
                                        [category.ids]:
                                            requirement === "FORBIDDEN"
                                                ? []
                                                : values[category.ids]
                                    });
                                }}
                                options={[
                                    { value: "REQUIRED", label: "Required" },
                                    { value: "OPTIONAL", label: "Optional" },
                                    { value: "FORBIDDEN", label: "Not used" }
                                ]}
                            />
                        </label>
                        <p className="field-note">
                            {values[category.requirement] === "REQUIRED"
                                ? "Every booking must include one."
                                : values[category.requirement] === "OPTIONAL"
                                  ? "Bookings can include one or leave it out."
                                  : "This service does not use this category."}
                        </p>
                        {values[category.requirement] !== "FORBIDDEN" && (
                            <input
                                className="input"
                                aria-label={`Search eligible ${category.label.toLowerCase()}`}
                                placeholder={`Search ${category.label.toLowerCase()}…`}
                                value={searches[category.kind]}
                                onChange={e =>
                                    setSearches({
                                        ...searches,
                                        [category.kind]: e.target.value
                                    })
                                }
                            />
                        )}
                        {values[category.requirement] !== "FORBIDDEN" && (
                            <div className="choice-list">
                                {catalogs[category.kind].length === 0 ? (
                                    <p className="field-note">
                                        None configured. Add{" "}
                                        {category.label.toLowerCase()} in its
                                        management tab.
                                    </p>
                                ) : (
                                    catalogs[category.kind]
                                        .filter(item =>
                                            item.name
                                                .toLowerCase()
                                                .includes(
                                                    searches[
                                                        category.kind
                                                    ].toLowerCase()
                                                )
                                        )
                                        .map(item => (
                                            <label
                                                className="check-row"
                                                key={item.id}
                                            >
                                                <input
                                                    type="checkbox"
                                                    checked={values[
                                                        category.ids
                                                    ].includes(item.id)}
                                                    disabled={
                                                        !item.active &&
                                                        !values[
                                                            category.ids
                                                        ].includes(item.id)
                                                    }
                                                    onChange={() =>
                                                        setValues({
                                                            ...values,
                                                            [category.ids]:
                                                                values[
                                                                    category.ids
                                                                ].includes(
                                                                    item.id
                                                                )
                                                                    ? values[
                                                                          category
                                                                              .ids
                                                                      ].filter(
                                                                          id =>
                                                                              id !==
                                                                              item.id
                                                                      )
                                                                    : [
                                                                          ...values[
                                                                              category
                                                                                  .ids
                                                                          ],
                                                                          item.id
                                                                      ]
                                                        })
                                                    }
                                                />
                                                {entityLabel(
                                                    item,
                                                    catalogs[category.kind]
                                                )}
                                                {!item.active && (
                                                    <span className="field-note">
                                                        Inactive
                                                    </span>
                                                )}
                                            </label>
                                        ))
                                )}
                            </div>
                        )}
                    </fieldset>
                ))}
            </div>
            {service && (
                <label className="check-row">
                    <input
                        type="checkbox"
                        checked={values.active}
                        onChange={e =>
                            setValues({ ...values, active: e.target.checked })
                        }
                    />
                    Service active
                </label>
            )}
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
            <div className="form-actions">
                <button
                    className="button button-secondary"
                    type="button"
                    onClick={onCancel}
                >
                    Cancel
                </button>
                <button className="button button-primary" disabled={submitting}>
                    {submitting
                        ? "Saving…"
                        : service
                          ? "Save changes"
                          : "Create service"}
                </button>
            </div>
        </form>
    );
}
