export type AssignmentRequirement = "REQUIRED" | "OPTIONAL" | "FORBIDDEN";
export interface CreateServiceRequest {
    name: string; description: string | null; durationMinutes: number; price: number | null; currency: string | null;
    staffIds: string[]; locationIds: string[]; resourceIds: string[];
    staffRequirement: AssignmentRequirement; locationRequirement: AssignmentRequirement; resourceRequirement: AssignmentRequirement;
}
export interface UpdateServiceRequest extends CreateServiceRequest { active: boolean }
export interface ServiceOffering extends UpdateServiceRequest { id: string; createdAt: string }