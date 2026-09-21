import keycloak from "./keycloak";

export type WorkspaceRole = "TENANT_ADMIN" | "STAFF" | "CUSTOMER";

export type Capability =
    | "workspace.access"
    | "dashboard.view"
    | "appointments.manage"
    | "customers.manage"
    | "waitlist.manage"
    | "catalog.view"
    | "catalog.manage"
    | "staff.view"
    | "staff.manage"
    | "availability.manage"
    | "settings.manage"
    | "privacy.manage";

const roleCapabilities: Record<WorkspaceRole, readonly Capability[]> = {
    TENANT_ADMIN: [
        "workspace.access",
        "dashboard.view",
        "appointments.manage",
        "customers.manage",
        "waitlist.manage",
        "catalog.view",
        "catalog.manage",
        "staff.view",
        "staff.manage",
        "availability.manage",
        "settings.manage",
        "privacy.manage"
    ],
    STAFF: [
        "workspace.access",
        "dashboard.view",
        "appointments.manage",
        "customers.manage",
        "waitlist.manage",
        "catalog.view",
        "staff.view"
    ],
    CUSTOMER: []
};

export function capabilitiesForRoles(roles: readonly string[]) {
    return new Set(
        roles.flatMap(role =>
            role in roleCapabilities
                ? roleCapabilities[role as WorkspaceRole]
                : []
        )
    );
}

export function currentRealmRoles() {
    return keycloak.tokenParsed?.realm_access?.roles ?? [];
}

export function hasCapability(capability: Capability) {
    return capabilitiesForRoles(currentRealmRoles()).has(capability);
}

export function primaryWorkspaceRole(): WorkspaceRole | null {
    const roles = currentRealmRoles();
    if (roles.includes("TENANT_ADMIN")) return "TENANT_ADMIN";
    if (roles.includes("STAFF")) return "STAFF";
    if (roles.includes("CUSTOMER")) return "CUSTOMER";
    return null;
}

export function canAccessWorkspace() {
    return hasCapability("workspace.access");
}

export function canManageWorkspace() {
    return hasCapability("catalog.manage");
}
