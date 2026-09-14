export type AssignmentKind = "staff" | "locations" | "resources";
export interface Assignment {
    id: string;
    name: string;
    active: boolean;
    createdAt: string;
    email?: string | null;
    phone?: string | null;
    addressLine?: string | null;
    city?: string | null;
    freeAgent?: boolean;
    locationIds?: string[];
}
export interface AssignmentCatalogs {
    staff: Assignment[];
    locations: Assignment[];
    resources: Assignment[];
}
