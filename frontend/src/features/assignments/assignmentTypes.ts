export type AssignmentKind = "staff" | "locations" | "resources";
export interface Assignment { id: string; name: string; active: boolean; createdAt: string; freeAgent?: boolean; locationIds?: string[] }
export interface AssignmentCatalogs { staff: Assignment[]; locations: Assignment[]; resources: Assignment[] }
