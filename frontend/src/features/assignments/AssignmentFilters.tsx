import { entityLabel } from "../assignments/entityLabels";
import SearchSelect from "../../components/SearchSelect";
import type { AssignmentCatalogs } from "./assignmentTypes";
import type { ServiceOffering } from "../services/serviceTypes";
export interface AssignmentFilterValues { staffId: string; locationId: string; resourceId: string }
interface Props { service: ServiceOffering; catalogs: AssignmentCatalogs; values: AssignmentFilterValues; onChange: (values: AssignmentFilterValues) => void }
export default function AssignmentFilters({ service, catalogs, values, onChange }: Props) {
    const categories = [
        { key: "staffId", label: "Staff", kind: "staff", ids: service.staffIds, requirement: service.staffRequirement },
        { key: "locationId", label: "Location", kind: "locations", ids: service.locationIds, requirement: service.locationRequirement },
        { key: "resourceId", label: "Resource", kind: "resources", ids: service.resourceIds, requirement: service.resourceRequirement },
    ] as const;
    function change(key: "staffId" | "locationId" | "resourceId", value: string) {
        const next = { ...values, [key]: value };
        const staff = catalogs.staff.find(member => member.id === next.staffId);
        if (staff?.freeAgent === false && next.locationId && !staff.locationIds?.includes(next.locationId)) {
            if (key === "staffId") next.locationId = ""; else next.staffId = "";
        }
        onChange(next);
    }
    return <div className="form-grid">{categories.filter(category => category.requirement !== "FORBIDDEN").map(category => {
        const eligible = catalogs[category.kind].filter(item => item.active && category.ids.includes(item.id));
        return <div className="form-field" key={category.key}>{category.label}<span className="field-note">{category.requirement === "REQUIRED" ? "Required in every booking" : "Optional"}</span><SearchSelect value={values[category.key]} onChange={value => change(category.key, value)} ariaLabel={`Booking ${category.label.toLowerCase()}`} options={[{ value: "", label: category.requirement === "REQUIRED" ? `Any eligible ${category.label.toLowerCase()}` : `Any combination, including no ${category.label.toLowerCase()}` }, ...eligible.map(item => ({ value: item.id, label: entityLabel(item, catalogs[category.kind]), description: category.kind === "staff" ? item.freeAgent === false ? "Assigned locations" : "Free agent" : undefined }))]} /></div>;
    })}</div>;
}
