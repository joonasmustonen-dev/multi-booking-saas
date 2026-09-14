import { apiFetch } from "../../api/apiClient";

import type {
    TenantSettings,
} from "./settingsTypes";


export function getTenantSettings() {
    return apiFetch<TenantSettings>(
        "/api/v1/settings"
    );
}


export function updateTenantSettings(
    settings: TenantSettings
) {
    return apiFetch<TenantSettings>(
        "/api/v1/settings",
        {
            method: "PUT",
            body: JSON.stringify(settings),
        }
    );
}