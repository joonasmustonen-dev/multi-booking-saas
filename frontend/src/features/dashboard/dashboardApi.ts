import {
    apiFetch,
} from "../../api/apiClient";

import type {
    DashboardSummary,
} from "./dashboardTypes";


export function getDashboardSummary() {

    return apiFetch<DashboardSummary>(
        "/api/v1/dashboard/summary"
    );
}