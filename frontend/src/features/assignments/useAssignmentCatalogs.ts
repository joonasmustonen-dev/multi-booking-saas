import { useQuery } from "@tanstack/react-query";
import { getStaff, getLocations } from "./assignmentApi";
import { getResources } from "../resources/resourceApi";
export function useAssignmentCatalogs() {
    const staff = useQuery({ queryKey: ["staff"], queryFn: getStaff });
    const locations = useQuery({
        queryKey: ["locations"],
        queryFn: getLocations
    });
    const resources = useQuery({
        queryKey: ["resources"],
        queryFn: getResources
    });
    return {
        isPending:
            staff.isPending || locations.isPending || resources.isPending,
        error: staff.error ?? locations.error ?? resources.error,
        data: {
            staff: staff.data ?? [],
            locations: locations.data ?? [],
            resources: resources.data ?? []
        }
    };
}
