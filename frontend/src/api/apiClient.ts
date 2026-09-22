import keycloak from "../auth/keycloak";
import { currentWorkspace } from "../auth/workspaceSession";

const API_URL = import.meta.env?.VITE_API_URL ?? "http://localhost:8080";

export async function apiFetch<T>(
    path: string,
    options: RequestInit = {}
): Promise<T> {
    try {
        await keycloak.updateToken(30);
    } catch {
        await keycloak.login();
        throw new Error("Authentication expired");
    }

    const headers = new Headers(options.headers);

    headers.set("Authorization", `Bearer ${keycloak.token}`);

    const workspace = currentWorkspace();
    if (workspace) headers.set("X-Workspace", workspace.slug);

    if (options.body) {
        headers.set("Content-Type", "application/json");
    }

    const response = await fetch(`${API_URL}${path}`, {
        ...options,
        headers
    });

    if (response.status === 401) {
        await keycloak.login();
        throw new Error("Unauthorized");
    }

    if (response.status === 403) {
        throw new Error("You do not have permission to perform this action");
    }

    if (!response.ok) {
        const problem = await response.json().catch(() => null);
        const detail =
            typeof problem?.detail === "string"
                ? problem.detail
                : response.status === 429
                  ? "Too many requests. Wait a minute and try again."
                  : `API request failed with status ${response.status}`;
        const requestId =
            response.status >= 500 && typeof problem?.requestId === "string"
                ? ` Request ID: ${problem.requestId}`
                : "";
        throw new Error(detail + requestId);
    }

    if (response.status === 204) {
        return undefined as T;
    }

    return response.json() as Promise<T>;
}
