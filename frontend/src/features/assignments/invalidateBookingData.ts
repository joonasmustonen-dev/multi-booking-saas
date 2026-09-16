import type { QueryClient } from "@tanstack/react-query";
export async function invalidateBookingData(client: QueryClient) {
    await Promise.all(
        [
            "services",
            "staff",
            "locations",
            "resources",
            "availability",
            "appointments",
            "dashboard-summary",
            "availability-rules",
            "availability-exceptions",
            "customer-activity",
            "customer",
            "customer-search",
            "privacy-audit",
            "staff-week",
            "waitlist"
        ].map(key => client.invalidateQueries({ queryKey: [key] }))
    );
}
