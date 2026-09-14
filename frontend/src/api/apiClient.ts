import keycloak from "../auth/keycloak";

const API_URL = "http://localhost:8080";

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
        throw new Error(`API request failed with status ${response.status}`);
    }

    if (response.status === 204) {
        return undefined as T;
    }

    return response.json() as Promise<T>;
}
