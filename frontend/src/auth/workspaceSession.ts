import keycloak from "./keycloak";
import type { WorkspaceRole } from "./permissions";

export interface WorkspaceSession {
    id: string;
    slug: string;
    name: string;
    role: WorkspaceRole;
    staffMemberId: string | null;
}

const API_URL = import.meta.env?.VITE_API_URL ?? "http://localhost:8080";
const STORAGE_KEY = "multibooking.workspace";

let workspaces: WorkspaceSession[] = [];
let selected: WorkspaceSession | null = null;
let initialized = false;

export function workspaceSessions() {
    return workspaces;
}

export function currentWorkspace() {
    return selected;
}

export function workspaceSessionInitialized() {
    return initialized;
}

export function selectWorkspace(workspace: WorkspaceSession) {
    selected = workspace;
    localStorage.setItem(STORAGE_KEY, workspace.slug);
}

export async function initializeWorkspaceSession() {
    const response = await fetch(`${API_URL}/api/account/workspaces`, {
        headers: { Authorization: `Bearer ${keycloak.token}` }
    });
    if (!response.ok) return;
    initialized = true;
    workspaces = (await response.json()) as WorkspaceSession[];
    if (workspaces.length === 0) return;

    const stored = localStorage.getItem(STORAGE_KEY);
    const claimed = keycloak.tokenParsed?.tenant_id as string | undefined;
    selected =
        workspaces.find(workspace => workspace.slug === stored) ??
        workspaces.find(workspace => workspace.slug === claimed) ??
        workspaces[0];
    localStorage.setItem(STORAGE_KEY, selected.slug);
}
