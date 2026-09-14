import type { QueryClient } from "@tanstack/react-query";
export async function invalidateBookingData(client: QueryClient) {
    await Promise.all(["services", "staff", "locations", "resources", "availability", "appointments", "dashboard-summary", "availability-rules", "availability-exceptions", "customer-activity", "staff-week"]
        .map(key => client.invalidateQueries({ queryKey: [key] })));
}
