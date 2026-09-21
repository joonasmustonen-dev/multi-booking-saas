import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, Plus, X } from "lucide-react";
import DateTimeInput from "../../components/DateTimeInput";
import SearchSelect from "../../components/SearchSelect";
import { getServices } from "../services/serviceApi";
import { getStaff } from "../staff/staffApi";
import { getLocations } from "../locations/locationApi";
import { formatDate, formatTime } from "../settings/timeFormat";
import { invalidateBookingData } from "../assignments/invalidateBookingData";
import { localDateTimeInZone } from "../availability/scheduleDates";
import {
    acceptWaitlistOffer,
    createWaitlistEntry,
    expireWaitlistEntry,
    getWaitlist,
    removeWaitlistEntry
} from "./waitlistApi";
import { CardListSkeleton } from "../../components/LoadingSkeletons";

interface Props {
    customerId: string;
    timeZone: string;
    disabled?: boolean;
    customerEmail?: string | null;
    customerPhone?: string | null;
    onAddContactDetails?: () => void;
}

function tomorrow(timeZone: string, hour: number) {
    const tenantToday = localDateTimeInZone(
        new Date().toISOString(),
        timeZone
    ).slice(0, 10);
    const [year, month, day] = tenantToday.split("-").map(Number);
    const date = new Date(Date.UTC(year, month - 1, day + 1));
    const pad = (value: number) => String(value).padStart(2, "0");
    return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${pad(hour)}:00`;
}

function channelLabel(channel: "EMAIL" | "SMS" | "IN_APP") {
    if (channel === "EMAIL") return "email queued";
    if (channel === "SMS") return "SMS queued";
    return "workspace only";
}

export default function WaitlistPanel({
    customerId,
    timeZone,
    disabled,
    customerEmail,
    customerPhone,
    onAddContactDetails
}: Props) {
    const client = useQueryClient();
    const list = useQuery({
        queryKey: ["waitlist", customerId],
        queryFn: () => getWaitlist(customerId)
    });
    const services = useQuery({ queryKey: ["services"], queryFn: getServices });
    const staff = useQuery({ queryKey: ["staff"], queryFn: getStaff });
    const locations = useQuery({ queryKey: ["locations"], queryFn: getLocations });
    const contactChannelKey = [customerEmail?.trim(), customerPhone?.trim()]
        .filter(Boolean)
        .join("|");
    const hasContactChannel = contactChannelKey.length > 0;
    const [adding, setAdding] = useState(false);
    const [serviceId, setServiceId] = useState("");
    const [staffId, setStaffId] = useState("");
    const [locationId, setLocationId] = useState("");
    const [start, setStart] = useState(() => tomorrow(timeZone, 9));
    const [end, setEnd] = useState(() => tomorrow(timeZone, 17));
    const [consentedForChannels, setConsentedForChannels] = useState<
        string | null
    >(null);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState("");
    const selectedService = services.data?.find(item => item.id === serviceId);
    const eligibleStaff = (staff.data ?? []).filter(item =>
        selectedService?.staffIds.includes(item.id)
    );
    const eligibleLocations = (locations.data ?? []).filter(item =>
        selectedService?.locationIds.includes(item.id)
    );
    const consent =
        hasContactChannel && consentedForChannels === contactChannelKey;

    async function refresh() {
        await invalidateBookingData(client);
        await client.invalidateQueries({ queryKey: ["waitlist"] });
    }
    async function run(action: () => Promise<unknown>) {
        if (pending) return;
        setPending(true);
        setError("");
        try { await action(); await refresh(); }
        catch (reason) { setError(reason instanceof Error ? reason.message : "Could not update waitlist."); }
        finally { setPending(false); }
    }
    function submit(event: FormEvent) {
        event.preventDefault();
        if (end <= start) { setError("The end of the range must be after its start."); return; }
        void run(async () => {
            await createWaitlistEntry({
                customerId, serviceId,
                preferredStaffId: staffId || null,
                preferredLocationId: locationId || null,
                windowStart: start, windowEnd: end, expiresAt: null,
                notificationConsent: consent && hasContactChannel
            });
            setAdding(false);
        });
    }

    return (
        <section className="waitlist-panel">
            <div className="section-heading">
                <div>
                    <h3>Waitlist</h3>
                    <p className="field-note">
                        Match this customer when a suitable cancellation opens
                        a slot.
                    </p>
                </div>
                <button
                    className="button button-secondary"
                    disabled={disabled || pending}
                    onClick={() => setAdding(value => !value)}
                >
                    {adding ? <X size={16} /> : <Plus size={16} />}
                    {adding ? "Close" : "Add request"}
                </button>
            </div>
            {adding && (
                <form className="waitlist-form" onSubmit={submit}>
                    <div className="form-grid">
                        <label className="form-field">
                            Service
                            <SearchSelect
                                required
                                value={serviceId}
                                ariaLabel="Waitlist service"
                                placeholder="Choose service"
                                options={(services.data ?? [])
                                    .filter(service => service.active)
                                    .map(service => ({
                                        value: service.id,
                                        label: service.name
                                    }))}
                                onChange={value => {
                                    setServiceId(value);
                                    setStaffId("");
                                    setLocationId("");
                                }}
                            />
                        </label>
                        <label className="form-field">
                            Preferred staff
                            <SearchSelect
                                value={staffId}
                                ariaLabel="Preferred staff"
                                placeholder="Any eligible staff"
                                options={[
                                    {
                                        value: "",
                                        label: "Any eligible staff"
                                    },
                                    ...eligibleStaff.map(member => ({
                                        value: member.id,
                                        label: member.name,
                                        description:
                                            member.email || member.phone
                                    }))
                                ]}
                                onChange={setStaffId}
                            />
                        </label>
                        <label className="form-field">
                            Preferred location
                            <SearchSelect
                                value={locationId}
                                ariaLabel="Preferred location"
                                placeholder="Any eligible location"
                                options={[
                                    {
                                        value: "",
                                        label: "Any eligible location"
                                    },
                                    ...eligibleLocations.map(location => ({
                                        value: location.id,
                                        label: location.name,
                                        description: [
                                            location.addressLine,
                                            location.city
                                        ]
                                            .filter(Boolean)
                                            .join(", ")
                                    }))
                                ]}
                                onChange={setLocationId}
                            />
                        </label>
                        <label className="form-field">
                            Range starts
                            <DateTimeInput
                                className="input"
                                type="datetime-local"
                                required
                                value={start}
                                onChange={event =>
                                    setStart(event.target.value)
                                }
                            />
                        </label>
                        <label className="form-field">
                            Range ends
                            <DateTimeInput
                                className="input"
                                type="datetime-local"
                                required
                                value={end}
                                onChange={event => setEnd(event.target.value)}
                            />
                        </label>
                    </div>
                    <div
                        className={`choice-card waitlist-consent-card ${
                            hasContactChannel ? "" : "is-disabled"
                        }`}
                    >
                        <input
                            id={`waitlist-consent-${customerId}`}
                            type="checkbox"
                            checked={consent && hasContactChannel}
                            disabled={!hasContactChannel}
                            onChange={event =>
                                setConsentedForChannels(
                                    event.target.checked
                                        ? contactChannelKey
                                        : null
                                )
                            }
                        />
                        <label htmlFor={`waitlist-consent-${customerId}`}>
                            <strong>Customer consents to notifications</strong>
                            <small>
                                {hasContactChannel
                                    ? "With consent, offers use email or SMS. Without it, offers remain visible only in this workspace."
                                    : "Add an email address or phone number to enable notifications. You can still save a workspace-only request."}
                            </small>
                        </label>
                        {!hasContactChannel && onAddContactDetails && (
                            <button
                                className="button button-secondary"
                                type="button"
                                onClick={onAddContactDetails}
                            >
                                Add contact details
                            </button>
                        )}
                    </div>
                    <div className="form-actions">
                        <button
                            className="button button-primary"
                            disabled={pending || !serviceId}
                        >
                            {pending ? "Saving…" : "Join waitlist"}
                        </button>
                    </div>
                </form>
            )}
            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}
            {list.isPending && (
                <CardListSkeleton rows={2} label="Loading waitlist" />
            )}
            {list.error && (
                <p className="form-error" role="alert">
                    {list.error.message}
                </p>
            )}
            <div className="waitlist-entries">
                {(list.data ?? []).map(entry => (
                    <article key={entry.id}>
                        <div className="waitlist-entry-main">
                            <BellRing size={18} />
                            <div>
                                <strong>{entry.serviceName}</strong>
                                <p>
                                    {formatDate(entry.windowStart, timeZone)}{" "}
                                    {formatTime(entry.windowStart, timeZone)} –{" "}
                                    {formatDate(entry.windowEnd, timeZone)}{" "}
                                    {formatTime(entry.windowEnd, timeZone)}
                                </p>
                                <small>
                                    {[
                                        entry.preferredStaffName,
                                        entry.preferredLocationName
                                    ]
                                        .filter(Boolean)
                                        .join(" · ") ||
                                        "Any eligible staff and location"}
                                </small>
                            </div>
                        </div>
                        <div className="waitlist-entry-actions">
                            <span
                                className={`waitlist-status waitlist-${entry.status.toLowerCase()}`}
                            >
                                {entry.status}
                            </span>
                            {entry.offer && entry.status === "OFFERED" && (
                                <p className="waitlist-offer">
                                    Offered {formatDate(entry.offer.startAt, timeZone)} at{" "}
                                    {formatTime(entry.offer.startAt, timeZone)} ·{" "}
                                    {channelLabel(entry.offer.notificationChannel)}
                                </p>
                            )}
                            {entry.status === "OFFERED" && (
                                <button
                                    className="button button-primary button-small"
                                    disabled={pending}
                                    onClick={() =>
                                        void run(() =>
                                            acceptWaitlistOffer(entry.id)
                                        )
                                    }
                                >
                                    Accept offer
                                </button>
                            )}
                            {(entry.status === "WAITING" ||
                                entry.status === "OFFERED") && (
                                <>
                                    <button
                                        className="button button-secondary button-small"
                                        disabled={pending}
                                        onClick={() =>
                                            void run(() =>
                                                expireWaitlistEntry(entry.id)
                                            )
                                        }
                                    >
                                        Expire
                                    </button>
                                    <button
                                        className="button button-danger button-small"
                                        disabled={pending}
                                        onClick={() =>
                                            void run(() =>
                                                removeWaitlistEntry(entry.id)
                                            )
                                        }
                                    >
                                        Remove
                                    </button>
                                </>
                            )}
                        </div>
                    </article>
                ))}
                {!list.isPending && !list.data?.length && (
                    <p className="field-note">
                        No waitlist requests for this customer.
                    </p>
                )}
            </div>
        </section>
    );
}
