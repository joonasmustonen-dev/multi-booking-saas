import "./LoadingSkeletons.css";

function Line({ width = "100%" }: { width?: string }) {
    return <span className="skeleton-line" style={{ width }} />;
}

export function CustomerListSkeleton() {
    return (
        <div
            className="customer-cards loading-skeleton"
            role="status"
            aria-label="Loading customers"
        >
            {Array.from({ length: 6 }, (_, index) => (
                <div className="skeleton-customer-card" key={index}>
                    <span className="skeleton-avatar" />
                    <span className="skeleton-stack">
                        <Line width="58%" />
                        <Line width="78%" />
                        <Line width="68%" />
                    </span>
                </div>
            ))}
        </div>
    );
}

export function CustomerDetailSkeleton() {
    return (
        <section
            className="customer-detail customer-detail-skeleton loading-skeleton"
            role="status"
            aria-label="Loading customer details"
        >
            <div className="skeleton-detail-header">
                <Line width="24%" />
                <span className="skeleton-title" />
                <Line width="54%" />
                <div className="skeleton-actions">
                    <span />
                    <span />
                </div>
            </div>
            <div className="skeleton-detail-content">
                <div className="skeleton-stat-grid">
                    {Array.from({ length: 4 }, (_, index) => (
                        <div key={index}>
                            <Line width="52%" />
                            <Line width="70%" />
                        </div>
                    ))}
                </div>
                <div className="skeleton-panel-card">
                    <Line width="32%" />
                    <Line />
                    <Line width="72%" />
                </div>
                <div className="skeleton-panel-card">
                    <Line width="38%" />
                    <Line width="88%" />
                </div>
            </div>
        </section>
    );
}

export function RecurringHoursSkeleton() {
    return (
        <div
            className="schedule-skeleton loading-skeleton"
            role="status"
            aria-label="Loading recurring hours"
        >
            {Array.from({ length: 7 }, (_, index) => (
                <div className="skeleton-day-card" key={index}>
                    <Line width="58%" />
                    <Line width="42%" />
                    <span className="skeleton-input" />
                    <span className="skeleton-input" />
                </div>
            ))}
        </div>
    );
}

export function CardListSkeleton({
    rows = 3,
    label
}: {
    rows?: number;
    label: string;
}) {
    return (
        <div
            className="card-list-skeleton loading-skeleton"
            role="status"
            aria-label={label}
        >
            {Array.from({ length: rows }, (_, index) => (
                <div className="skeleton-list-card" key={index}>
                    <span className="skeleton-avatar skeleton-avatar-small" />
                    <span className="skeleton-stack">
                        <Line width="46%" />
                        <Line width="82%" />
                    </span>
                    <span className="skeleton-action" />
                </div>
            ))}
        </div>
    );
}
