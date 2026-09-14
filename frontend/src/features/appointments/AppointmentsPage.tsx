import { useSearchParams } from "react-router-dom";
import { useCallback, useMemo, useState } from "react";
import {
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    Plus,
    X,
    Layers,
    Clock3
} from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    cancelAppointment,
    createAppointment,
    getAppointments,
    rescheduleAppointment,
    updateAppointmentStatus
} from "./appointmentApi";
import { getServices } from "../services/serviceApi";
import type {
    AppointmentCalendarItem,
    AppointmentStatus,
    RescheduleAppointmentRequest
} from "./appointmentTypes";
import { addDays, startOfWeek } from "./dateUtils";
import { dateKeyInTimeZone, layoutDayAppointments } from "./calendarLayout";
import {
    calendarDisplayGroups,
    calendarTickMinutes
} from "./calendarPresentation";
import { formatDate, formatTime } from "../settings/timeFormat";
import { useTenantSettings } from "../settings/useTenantSettings";
import { useAssignmentCatalogs } from "../assignments/useAssignmentCatalogs";
import { invalidateBookingData } from "../assignments/invalidateBookingData";
import { appointmentAssignmentLabel } from "../assignments/assignmentLabels";
import { entityLabel } from "../assignments/entityLabels";
import SearchSelect from "../../components/SearchSelect";
import BookingForm from "./BookingForm";
import RescheduleForm from "./RescheduleForm";
import AppointmentDetails from "./AppointmentDetails";
import "./AppointmentsPage.css";
import "./CalendarRefinement.css";

const statuses: AppointmentStatus[] = [
    "PENDING",
    "CONFIRMED",
    "CANCELLED",
    "COMPLETED",
    "NO_SHOW"
];
const statusLabel = (status: string) =>
    status
        .toLowerCase()
        .replace("_", " ")
        .replace(/^./, c => c.toUpperCase());
function serviceTone(id: string) {
    return ["sage", "sky", "lilac", "butter", "blush"][
        [...id].reduce((sum, c) => sum + c.charCodeAt(0), 0) % 5
    ];
}
function dayName(date: string) {
    return new Intl.DateTimeFormat("en", {
        weekday: "short",
        day: "numeric",
        timeZone: "UTC"
    }).format(new Date(`${date}T12:00:00Z`));
}
export default function AppointmentsPage() {
    const [params, setParams] = useSearchParams();
    const openCreate = params.get("openCreate") === "1";

    const client = useQueryClient();
    const catalogs = useAssignmentCatalogs();
    const settings = useTenantSettings();
    const services = useQuery({ queryKey: ["services"], queryFn: getServices });
    const [selectedDate, setSelectedDate] = useState("");
    const [view, setView] = useState<"week" | "day" | "agenda">("week");
    const [staff, setStaff] = useState("");
    const [location, setLocation] = useState("");
    const [service, setService] = useState("");
    const [status, setStatus] = useState<AppointmentStatus | "">("");
    const [createOpen, setCreateOpen] = useState(false);
    const creating = createOpen || openCreate;
    function setCreating(value: boolean) {
        setCreateOpen(value);
        if (openCreate) {
            const next = new URLSearchParams(params);
            next.delete("openCreate");
            setParams(next, { replace: true });
        }
    }
    const [selected, setSelected] = useState<AppointmentCalendarItem | null>(
        null
    );
    const [rescheduling, setRescheduling] =
        useState<AppointmentCalendarItem | null>(null);
    const [overlap, setOverlap] = useState<AppointmentCalendarItem[] | null>(
        null
    );
    const [bodyHeight, setBodyHeight] = useState(480);
    const calendarRef = useCallback((node: HTMLDivElement | null) => {
        if (!node) return;
        const measure = () =>
            setBodyHeight(Math.max(64, node.clientHeight - 68));
        measure();
        const observer = new ResizeObserver(measure);
        observer.observe(node);
        return () => observer.disconnect();
    }, []);
    const zone = settings.data?.timeZone ?? "UTC";
    const today = dateKeyInTimeZone(new Date().toISOString(), zone);
    const date = selectedDate || today;
    const weekStart = startOfWeek(date, settings.data?.weekStartsOn ?? 1);
    const weekEnd = addDays(weekStart, 6);
    const days = useMemo(
        () => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)),
        [weekStart]
    );
    const appointments = useQuery({
        queryKey: [
            "appointments",
            weekStart,
            weekEnd,
            staff,
            location,
            service,
            status
        ],
        queryFn: () =>
            getAppointments(weekStart, weekEnd, {
                staffId: staff || undefined,
                locationId: location || undefined,
                serviceId: service || undefined,
                status: status || undefined
            }),
        enabled: !!settings.data
    });
    const layouts = useMemo(
        () =>
            Object.fromEntries(
                days.map(day => [
                    day,
                    layoutDayAppointments(
                        (appointments.data ?? []).filter(
                            a => dateKeyInTimeZone(a.startAt, zone) === day
                        ),
                        zone
                    )
                ])
            ),
        [days, appointments.data, zone]
    );
    const refresh = () => invalidateBookingData(client);
    const create = useMutation({
        mutationFn: createAppointment,
        onSuccess: async () => {
            await refresh();
            setCreating(false);
        }
    });
    const cancel = useMutation({
        mutationFn: cancelAppointment,
        onSuccess: async () => {
            await refresh();
            setSelected(null);
        }
    });
    const changeStatus = useMutation({
        mutationFn: ({ id, value }: { id: string; value: AppointmentStatus }) =>
            updateAppointmentStatus(id, value),
        onSuccess: async () => {
            await refresh();
            setSelected(null);
        }
    });
    const reschedule = useMutation({
        mutationFn: ({
            id,
            request
        }: {
            id: string;
            request: RescheduleAppointmentRequest;
        }) => rescheduleAppointment(id, request),
        onSuccess: async () => {
            await refresh();
            setRescheduling(null);
            setSelected(null);
        }
    });
    const visibleDays = view === "day" ? [date] : days;
    const positioned = visibleDays.flatMap(
        day => layouts[day]?.appointments ?? []
    );
    const start = Math.max(
        0,
        Math.min(
            (settings.data?.calendarStartHour ?? 8) * 60,
            ...positioned.map(a => Math.floor(a.startMinute / 60) * 60)
        )
    );
    const end = Math.min(
        1440,
        Math.max(
            (settings.data?.calendarEndHour ?? 18) * 60,
            ...positioned.map(a => Math.ceil(a.endMinute / 60) * 60)
        )
    );
    const timelineHeight = Math.max(40, bodyHeight - 24);
    const scale = timelineHeight / Math.max(1, end - start);
    const ticks = calendarTickMinutes(start, end, timelineHeight);
    const hover = (a: AppointmentCalendarItem) =>
        `${a.customerName}\n${a.serviceName}\n${formatTime(a.startAt, zone)}–${formatTime(a.endAt, zone)}\n${appointmentAssignmentLabel(a)}\n${statusLabel(a.status)}`;
    const loading =
        catalogs.isPending || settings.isPending || services.isPending;
    const error = catalogs.error ?? settings.error ?? services.error;
    if (loading) return <p>Loading calendar…</p>;
    if (error || !settings.data)
        return (
            <p className="form-error" role="alert">
                {error?.message ?? "Could not load calendar."}
            </p>
        );
    function card(a: AppointmentCalendarItem, compact = false) {
        return (
            <>
                <span className="booking-card-top">
                    <span>
                        {formatTime(a.startAt, zone)}
                        {!compact && `–${formatTime(a.endAt, zone)}`}
                    </span>
                    <i
                        className={`status-dot status-${a.status.toLowerCase()}`}
                    />
                </span>
                <strong className="booking-card-customer">
                    {a.customerName}
                </strong>
                {!compact && (
                    <span className="booking-card-service">
                        {a.serviceName}
                    </span>
                )}
            </>
        );
    }
    return (
        <div className="calendar-page refined-calendar">
            <header className="calendar-page-header">
                <div className="calendar-header-main">
                    <div className="calendar-title-group">
                        <div>
                            <p className="page-eyebrow">
                                {formatDate(`${weekStart}T12:00:00Z`, zone)} —{" "}
                                {formatDate(`${weekEnd}T12:00:00Z`, zone)}
                            </p>
                            <h1 className="page-title">Appointments</h1>
                            <p className="page-description">
                                {statusLabel(view)} view · {zone}
                            </p>
                        </div>
                        <div className="calendar-title-icon">
                            <CalendarDays size={23} />
                        </div>
                    </div>
                    <button
                        className="button button-primary"
                        onClick={() => setCreating(true)}
                    >
                        <Plus size={18} />
                        New appointment
                    </button>
                </div>
                <div className="calendar-toolbar">
                    <div className="calendar-navigation">
                        <button
                            className="button button-secondary"
                            onClick={() =>
                                setSelectedDate(
                                    addDays(date, view === "day" ? -1 : -7)
                                )
                            }
                        >
                            <ChevronLeft size={16} />
                            Previous
                        </button>
                        <button
                            className="button button-secondary"
                            onClick={() => setSelectedDate(today)}
                        >
                            Today
                        </button>
                        <button
                            className="button button-secondary"
                            onClick={() =>
                                setSelectedDate(
                                    addDays(date, view === "day" ? 1 : 7)
                                )
                            }
                        >
                            Next
                            <ChevronRight size={16} />
                        </button>
                    </div>
                    <div className="calendar-filters">
                        <SearchSelect
                            className="calendar-filter"
                            value={staff}
                            onChange={setStaff}
                            ariaLabel="Calendar staff"
                            options={[
                                { value: "", label: "All staff" },
                                ...catalogs.data.staff.map(s => ({
                                    value: s.id,
                                    label: entityLabel(s, catalogs.data.staff)
                                }))
                            ]}
                        />
                        <SearchSelect
                            className="calendar-filter"
                            value={location}
                            onChange={setLocation}
                            ariaLabel="Calendar locations"
                            options={[
                                { value: "", label: "All locations" },
                                ...catalogs.data.locations.map(l => ({
                                    value: l.id,
                                    label: entityLabel(
                                        l,
                                        catalogs.data.locations
                                    )
                                }))
                            ]}
                        />
                        <SearchSelect
                            className="calendar-filter"
                            value={service}
                            onChange={setService}
                            ariaLabel="Calendar services"
                            options={[
                                { value: "", label: "All services" },
                                ...(services.data ?? []).map(s => ({
                                    value: s.id,
                                    label: entityLabel(s, services.data ?? [])
                                }))
                            ]}
                        />
                        <SearchSelect
                            className="calendar-filter"
                            searchable={false}
                            value={status}
                            onChange={v =>
                                setStatus(v as AppointmentStatus | "")
                            }
                            ariaLabel="Calendar statuses"
                            options={[
                                { value: "", label: "All statuses" },
                                ...statuses.map(s => ({
                                    value: s,
                                    label: statusLabel(s),
                                    tone: s.toLowerCase()
                                }))
                            ]}
                        />
                    </div>
                </div>
                <div className="calendar-view-bar">
                    <div
                        className="segmented-control"
                        aria-label="Calendar view"
                    >
                        {(["week", "day", "agenda"] as const).map(value => (
                            <button
                                key={value}
                                aria-pressed={view === value}
                                onClick={() => setView(value)}
                            >
                                {statusLabel(value)}
                            </button>
                        ))}
                    </div>
                    <span className="field-note">
                        {(appointments.data ?? []).length} bookings · Select a
                        day for more detail
                    </span>
                </div>
            </header>
            {appointments.error && (
                <p className="form-error" role="alert">
                    {appointments.error.message}
                </p>
            )}
            {appointments.isFetching && (
                <p className="calendar-loading" role="status">
                    Updating calendar…
                </p>
            )}
            {view === "agenda" ? (
                <div className="calendar-agenda">
                    {days.map(day => (
                        <section className="agenda-day" key={day}>
                            <h3>{dayName(day)}</h3>
                            {layouts[day].appointments.length ? (
                                layouts[day].appointments.map(
                                    ({ appointment: a }) => (
                                        <button
                                            className="agenda-booking"
                                            key={a.id}
                                            onClick={() => setSelected(a)}
                                            data-tooltip={hover(a)}
                                        >
                                            <span>
                                                <Clock3 size={15} />
                                                {formatTime(a.startAt, zone)}–
                                                {formatTime(a.endAt, zone)}
                                            </span>
                                            <div>
                                                <strong>
                                                    {a.customerName}
                                                </strong>
                                                <small>
                                                    {a.serviceName} ·{" "}
                                                    {appointmentAssignmentLabel(
                                                        a
                                                    )}
                                                </small>
                                            </div>
                                            <span
                                                className={`appointment-status status-${a.status.toLowerCase()}`}
                                            >
                                                {statusLabel(a.status)}
                                            </span>
                                        </button>
                                    )
                                )
                            ) : (
                                <p className="field-note">No appointments</p>
                            )}
                        </section>
                    ))}
                </div>
            ) : (
                <div ref={calendarRef} className="calendar-shell">
                    <div
                        className={`calendar-grid ${view === "day" ? "single-day" : ""}`}
                        style={{
                            gridTemplateColumns: `64px repeat(${visibleDays.length}, minmax(0, 1fr))`,
                            gridTemplateRows: "68px minmax(0, 1fr)"
                        }}
                    >
                        <div className="calendar-time-header">
                            <small>Time</small>
                        </div>
                        {visibleDays.map(day => (
                            <button
                                className={`calendar-day-header ${day === today ? "is-today" : ""}`}
                                key={day}
                                onClick={() => {
                                    setSelectedDate(day);
                                    setView(view === "day" ? "week" : "day");
                                }}
                            >
                                <strong className="calendar-day-name">
                                    {dayName(day)}
                                </strong>
                                <span className="calendar-day-count">
                                    {layouts[day]?.appointments.length ?? 0}{" "}
                                    {(layouts[day]?.appointments.length ??
                                        0) === 1
                                        ? "appointment"
                                        : "appointments"}
                                </span>
                            </button>
                        ))}
                        <div className="calendar-time-column">
                            {ticks.map((tick, i) => (
                                <span
                                    className={`calendar-hour-label ${i === 0 ? "first-tick" : i === ticks.length - 1 ? "last-tick" : ""}`}
                                    key={tick}
                                    style={{ top: 12 + (tick - start) * scale }}
                                >{`${String(Math.floor(tick / 60)).padStart(2, "0")}:00`}</span>
                            ))}
                        </div>
                        {visibleDays.map(day => (
                            <div
                                className={`calendar-day-column ${day === today ? "is-today" : ""}`}
                                key={day}
                            >
                                {ticks.map(tick => (
                                    <div
                                        className="calendar-hour-line"
                                        key={tick}
                                        style={{
                                            top: 12 + (tick - start) * scale
                                        }}
                                    />
                                ))}
                                {calendarDisplayGroups(
                                    layouts[day],
                                    start,
                                    end,
                                    timelineHeight,
                                    view === "day" ? 5 : 2
                                ).flatMap(group =>
                                    group.grouped
                                        ? [
                                              <button
                                                  key={`group-${group.items[0].appointment.id}`}
                                                  className="calendar-booking-group"
                                                  style={{
                                                      top: 12 + group.top,
                                                      height: group.height
                                                  }}
                                                  onClick={() =>
                                                      setOverlap(
                                                          group.items.map(
                                                              item =>
                                                                  item.appointment
                                                          )
                                                      )
                                                  }
                                                  data-tooltip={`${group.items.length} bookings\n${group.items.map(item => `${formatTime(item.appointment.startAt, zone)} · ${item.appointment.customerName}`).join("\n")}`}
                                              >
                                                  <Layers size={15} />
                                                  <strong>
                                                      {group.items.length}{" "}
                                                      bookings
                                                  </strong>
                                                  <small>Click to browse</small>
                                              </button>
                                          ]
                                        : group.items.map(item => {
                                              const a = item.appointment;
                                              const height = Math.min(
                                                  timelineHeight - group.top,
                                                  Math.max(
                                                      38,
                                                      (item.endMinute -
                                                          item.startMinute) *
                                                          scale -
                                                          4
                                                  )
                                              );
                                              const top = Math.max(
                                                  group.top,
                                                  Math.min(
                                                      timelineHeight - height,
                                                      (item.startMinute -
                                                          start) *
                                                          scale
                                                  )
                                              );
                                              return (
                                                  <button
                                                      key={a.id}
                                                      className={`calendar-appointment tone-${serviceTone(a.serviceId)} ${height < 65 || (view === "week" && item.laneCount > 1) ? "compact-booking" : ""}`}
                                                      data-tooltip={hover(a)}
                                                      style={{
                                                          top: 12 + top,
                                                          height,
                                                          left: `calc(${(item.lane / item.laneCount) * 100}% + 5px)`,
                                                          width: `calc(${100 / item.laneCount}% - 10px)`
                                                      }}
                                                      onClick={() =>
                                                          setSelected(a)
                                                      }
                                                  >
                                                      {card(
                                                          a,
                                                          height < 65 ||
                                                              (view ===
                                                                  "week" &&
                                                                  item.laneCount >
                                                                      1)
                                                      )}
                                                  </button>
                                              );
                                          })
                                )}
                                {!layouts[day].appointments.length && (
                                    <span className="calendar-empty-day">
                                        No appointments
                                    </span>
                                )}
                            </div>
                        ))}
                    </div>
                </div>
            )}
            {creating && (
                <div
                    className="calendar-modal-backdrop"
                    onMouseDown={() => setCreating(false)}
                >
                    <div
                        className="calendar-modal"
                        onMouseDown={e => e.stopPropagation()}
                    >
                        <BookingForm
                            onSubmit={request =>
                                create
                                    .mutateAsync(request)
                                    .then(() => undefined)
                            }
                            onCancel={() => setCreating(false)}
                        />
                    </div>
                </div>
            )}
            {rescheduling && (
                <div
                    className="calendar-modal-backdrop"
                    onMouseDown={() => setRescheduling(null)}
                >
                    <div
                        className="calendar-modal"
                        onMouseDown={e => e.stopPropagation()}
                    >
                        <RescheduleForm
                            appointment={rescheduling}
                            timeZone={zone}
                            onSubmit={request =>
                                reschedule
                                    .mutateAsync({
                                        id: rescheduling.id,
                                        request
                                    })
                                    .then(() => undefined)
                            }
                            onCancel={() => setRescheduling(null)}
                        />
                    </div>
                </div>
            )}
            {selected && (
                <AppointmentDetails
                    key={selected.id}
                    appointment={selected}
                    timeZone={zone}
                    onClose={() => setSelected(null)}
                    onReschedule={() => {
                        setRescheduling(selected);
                        setSelected(null);
                    }}
                    onCancel={() => cancel.mutateAsync(selected.id)}
                    onStatus={value =>
                        changeStatus.mutateAsync({ id: selected.id, value })
                    }
                />
            )}
            {overlap && (
                <div
                    className="calendar-modal-backdrop"
                    onMouseDown={() => setOverlap(null)}
                >
                    <section
                        className="calendar-modal grouped-bookings-dialog"
                        role="dialog"
                        aria-modal="true"
                        aria-label="Grouped bookings"
                        onMouseDown={e => e.stopPropagation()}
                    >
                        <div className="section-heading">
                            <div>
                                <p className="page-eyebrow">
                                    Appointments at a glance
                                </p>
                                <h2>{overlap.length} bookings</h2>
                            </div>
                            <button
                                className="drawer-close"
                                aria-label="Close grouped bookings"
                                onClick={() => setOverlap(null)}
                            >
                                <X size={18} />
                            </button>
                        </div>
                        <p className="field-note">
                            Switch to day or agenda view for more room.
                        </p>
                        <div className="overlap-list">
                            {overlap.map(a => (
                                <button
                                    className="overlap-list-item"
                                    key={a.id}
                                    onClick={() => {
                                        setOverlap(null);
                                        setSelected(a);
                                    }}
                                >
                                    <div>
                                        <strong>{a.customerName}</strong>
                                        <p>{a.serviceName}</p>
                                        <small>
                                            {appointmentAssignmentLabel(a)}
                                        </small>
                                    </div>
                                    <span>
                                        {formatTime(a.startAt, zone)}
                                        <i
                                            className={`status-dot status-${a.status.toLowerCase()}`}
                                        />
                                    </span>
                                </button>
                            ))}
                        </div>
                    </section>
                </div>
            )}
        </div>
    );
}
