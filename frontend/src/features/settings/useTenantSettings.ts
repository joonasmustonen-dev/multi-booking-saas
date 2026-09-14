import { useQuery } from "@tanstack/react-query";

import { getTenantSettings }
    from "./settingsApi";

export function useTenantSettings() {

    return useQuery({
        queryKey: ["tenant-settings"],
        queryFn: getTenantSettings,
        staleTime: 5 * 60 * 1000,
    });
}