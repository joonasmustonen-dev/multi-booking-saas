import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { getCustomerActivity } from "./customerApi";
import { formatDate } from "../settings/timeFormat";

export default function CustomerContext({
    customerId,
    timeZone
}: {
    customerId: string;
    timeZone: string;
}) {
    const query = useQuery({
        queryKey: ["customer-activity", customerId, 0, 1],
        queryFn: () => getCustomerActivity(customerId, 0, 1),
        enabled: !!customerId
    });
    if (!customerId) return null;
    if (query.isPending)
        return <p className="field-note">Loading customer context…</p>;
    if (query.error)
        return (
            <p className="field-note">Customer context could not be loaded.</p>
        );
    const data = query.data;
    return (
        <section className="customer-context">
            <div className="section-heading">
                <strong>Customer context</strong>
                <Link to={`/customers?customerId=${customerId}`}>
                    View customer
                </Link>
            </div>
            <div className="customer-context-facts">
                <span>
                    Last visit
                    <strong>
                        {data.lastVisit
                            ? formatDate(data.lastVisit, timeZone)
                            : "No completed visits"}
                    </strong>
                </span>
                <span>
                    Preferred staff
                    <strong>
                        {data.preferredStaffName || "No preference"}
                    </strong>
                </span>
                <span>
                    Attendance
                    <strong>
                        {data.completedBookings} completed · {data.noShows}{" "}
                        no-shows
                    </strong>
                </span>
            </div>
            {!!data.mostBookedServices.length && (
                <p className="field-note">
                    Usual services:{" "}
                    {data.mostBookedServices
                        .map(s => `${s.name} (${s.visits})`)
                        .join(" · ")}
                </p>
            )}
        </section>
    );
}
