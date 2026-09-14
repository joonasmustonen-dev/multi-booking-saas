import keycloak from "./keycloak";

export function canManageWorkspace() {
    return (
        keycloak.tokenParsed?.realm_access?.roles?.includes("TENANT_ADMIN") ??
        false
    );
}
