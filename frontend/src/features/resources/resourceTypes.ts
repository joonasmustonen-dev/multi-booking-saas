export type ResourceType = "EQUIPMENT" | "VEHICLE" | "OTHER";

export interface BookableResource {
    id: string;
    name: string;
    description: string;
    type: ResourceType;
    active: boolean;
    createdAt: string;
}

export interface ResourceRequest {
    name: string;
    description: string;
    type: ResourceType;
    active: boolean;
}
