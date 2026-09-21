import { useState } from "react";
import { canManageWorkspace } from "../../auth/permissions";
import CustomerPrivacyPanel from "./CustomerPrivacyPanel";
import BookingForm from "../appointments/BookingForm";
import { createAppointment } from "../appointments/appointmentApi";
import { invalidateBookingData } from "../assignments/invalidateBookingData";
import { useQueryClient } from "@tanstack/react-query";
import { useQuery } from "@tanstack/react-query";
import { Mail, Phone, UserRound, Clock3 } from "lucide-react";
import { getCustomerActivity } from "./customerApi";
import { formatDate, formatTime } from "../settings/timeFormat";
import type { Customer } from "./customerTypes";
import WaitlistPanel from "../waitlist/WaitlistPanel";
import { CustomerDetailSkeleton } from "../../components/LoadingSkeletons";

export default function CustomerDetail({
    id,
    timeZone,
    onEdit,
    onClose,
    onDelete
}: {
    id: string;
    timeZone: string;
    onEdit: (customer: Customer) => void;
    onClose: () => void;
    onDelete?: (id: string) => Promise<void>;
}) {
    const client = useQueryClient();
    const [booking, setBooking] = useState(false);
    const [page, setPage] = useState(0);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState("");
    async function remove() {
        if (!onDelete || deleting || !window.confirm("Delete this customer?"))
            return;
        setDeleting(true);
        setDeleteError("");
        try {
            await onDelete(id);
        } catch (e) {
            setDeleteError(
                e instanceof Error ? e.message : "Could not delete customer."
            );
        } finally {
            setDeleting(false);
        }
    }
    const query = useQuery({
        queryKey: ["customer-activity", id, page, 20],
        queryFn: () => getCustomerActivity(id, page)
    });
    if (query.isPending) return <CustomerDetailSkeleton />;
    if (query.error)
        return (
            <section className="customer-detail">
                <p className="form-error" role="alert">
                    {query.error.message}
                </p>
                <button className="button button-secondary" onClick={onClose}>
                    Close
                </button>
            </section>
        );
    const d = query.data;
    const c = d.customer;
    function editCustomer() {
        onEdit(c);
        requestAnimationFrame(() =>
            document
                .querySelector(".customer-form")
                ?.scrollIntoView({ behavior: "smooth", block: "start" })
        );
    }
    return (
        <section className="customer-detail" aria-label="Customer details">
            <header className="customer-detail-header">
                <div className="section-heading">
                    <span className="page-eyebrow">Customer details</span>
                    <button
                        className="button button-secondary"
                        onClick={onClose}
                    >
                        Close
                    </button>
                </div>
                <h2>
                    {c.firstName} {c.lastName}
                </h2>
                <div className="customer-contact-links">
                    {c.email && (
                        <a href={`mailto:${c.email}`}>
                            <Mail size={16} />
                            {c.email}
                        </a>
                    )}
                    {c.phone && (
                        <a href={`tel:${c.phone}`}>
                            <Phone size={16} />
                            {c.phone}
                        </a>
                    )}
                </div>
                <div className="form-actions">
                    <button
                        className="button button-primary"
                        disabled={!!c.processingRestricted || !!c.erasedAt}
                        onClick={() => setBooking(true)}
                    >
                        New appointment
                    </button>
                    <button
                        className="button button-secondary"
                        disabled={!!c.erasedAt}
                        onClick={editCustomer}
                    >
                        Edit customer
                    </button>
                </div>
            </header>
            <div className="customer-detail-content">
                {c.processingRestricted && (
                    <p className="booking-summary">
                        Customer processing is restricted. New bookings and
                        rescheduling are paused.
                    </p>
                )}
                {canManageWorkspace() && (
                    <CustomerPrivacyPanel
                        customer={c}
                        onErased={onClose}
                        onChanged={async () => {
                            await Promise.all(
                                [
                                    "customer",
                                    "customer-search",
                                    "customer-activity"
                                ].map(key =>
                                    client.invalidateQueries({
                                        queryKey: [key]
                                    })
                                )
                            );
                            await invalidateBookingData(client);
                        }}
                    />
                )}
                <div className="customer-stats">
                    <div>
                        <Clock3 size={18} />
                        <small>Last visit</small>
                        <strong>
                            {d.lastVisit
                                ? formatDate(d.lastVisit, timeZone)
                                : "No completed visits"}
                        </strong>
                    </div>
                    <div>
                        <small>Completed</small>
                        <strong>{d.completedBookings}</strong>
                    </div>
                    <div>
                        <small>Cancelled</small>
                        <strong>{d.cancelledBookings}</strong>
                    </div>
                    <div>
                        <small>No-shows</small>
                        <strong>{d.noShows}</strong>
                    </div>
                </div>
                <p className="customer-preference">
                    <UserRound size={17} />
                    Preferred staff:{" "}
                    <strong>{d.preferredStaffName || "No preference"}</strong>
                </p>
                <WaitlistPanel
                    customerId={id}
                    timeZone={timeZone}
                    disabled={!!c.processingRestricted || !!c.erasedAt}
                    customerEmail={c.email}
                    customerPhone={c.phone}
                    onAddContactDetails={editCustomer}
                />
                <h3>Most-booked services</h3>
                <p className="field-note">Based on completed visits.</p>
                {d.mostBookedServices.length ? (
                    <div className="selected-tags">
                        {d.mostBookedServices.map(s => (
                            <span className="selected-tag" key={s.serviceId}>
                                {s.name} · {s.visits} visits
                            </span>
                        ))}
                    </div>
                ) : (
                    <p className="field-note">
                        No completed service history yet.
                    </p>
                )}
                <div className="section-heading">
                    <h3>Booking history</h3>
                    <span className="field-note">
                        {d.totalBookings} bookings
                    </span>
                </div>
                {!d.bookings.length && (
                    <p className="field-note">No bookings on this page.</p>
                )}
                <div className="customer-history">
                    {d.bookings.map(b => (
                        <article key={b.id}>
                            <div>
                                <strong>{b.serviceName}</strong>
                                <p>
                                    {formatDate(b.startAt, timeZone)} ·{" "}
                                    {formatTime(b.startAt, timeZone)}–
                                    {formatTime(b.endAt, timeZone)}
                                </p>
                                <small>
                                    {[
                                        b.staffName,
                                        b.locationName,
                                        b.resourceName
                                    ]
                                        .filter(Boolean)
                                        .join(" · ") || "No assignments"}
                                </small>
                            </div>
                            <span
                                className={`appointment-status status-${b.status.toLowerCase()}`}
                            >
                                {b.status.replace("_", " ")}
                            </span>
                        </article>
                    ))}
                </div>
                <div className="form-actions">
                    <button
                        className="button button-secondary"
                        disabled={page === 0}
                        onClick={() => setPage(page - 1)}
                    >
                        Previous
                    </button>
                    <span className="field-note">
                        Page {page + 1} of {Math.max(1, d.totalPages)}
                    </span>
                    <button
                        className="button button-secondary"
                        disabled={page + 1 >= d.totalPages}
                        onClick={() => setPage(page + 1)}
                    >
                        Next
                    </button>
                </div>
                {onDelete && canManageWorkspace() && (
                    <div className="customer-delete-section">
                        <button
                            className="button button-danger"
                            disabled={
                                deleting || d.totalBookings > 0 || !!c.legalHold
                            }
                            onClick={() => void remove()}
                        >
                            {deleting ? "Deleting…" : "Delete customer"}
                        </button>
                        {d.totalBookings > 0 && (
                            <p className="field-note">
                                Customers with booking history cannot be deleted
                                here.
                            </p>
                        )}
                        {deleteError && (
                            <p role="alert" className="form-error">
                                {deleteError}
                            </p>
                        )}
                    </div>
                )}
            </div>
            {booking && (
                <div
                    className="calendar-modal-backdrop"
                    onMouseDown={() => setBooking(false)}
                >
                    <div
                        className="calendar-modal"
                        role="dialog"
                        aria-modal="true"
                        aria-label="Create appointment for customer"
                        onMouseDown={e => e.stopPropagation()}
                    >
                        <BookingForm
                            initialCustomerId={id}
                            onCancel={() => setBooking(false)}
                            onSubmit={async request => {
                                await createAppointment(request);
                                await invalidateBookingData(client);
                                setBooking(false);
                            }}
                        />
                    </div>
                </div>
            )}
        </section>
    );
}
