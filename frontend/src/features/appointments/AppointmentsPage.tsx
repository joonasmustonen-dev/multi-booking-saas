import {
    useMemo,
    useCallback,
    useState,
} from "react";

import {
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    Plus,
    Users,
    X,
} from "lucide-react";

import {
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query";

import {
    cancelAppointment,
    createAppointment,
    getAppointments,
    rescheduleAppointment,
    updateAppointmentStatus,
} from "./appointmentApi";

import {
    getServices,
} from "../services/serviceApi";

import type {
    AppointmentCalendarItem,
    AppointmentStatus,
    CreateAppointmentRequest,
} from "./appointmentTypes";

import {
    addDays,
    startOfWeek,
} from "./dateUtils";

import {
    dateKeyInTimeZone,
    layoutDayAppointments,
} from "./calendarLayout";

import {
    formatTime,
} from "../settings/timeFormat";

import {
    useTenantSettings,
} from "../settings/useTenantSettings";

import BookingForm
    from "./BookingForm";

import RescheduleForm
    from "./RescheduleForm";

import "./AppointmentsPage.css";
import AppointmentDetails from "./AppointmentDetails";
import SearchSelect from "../../components/SearchSelect";
import { useAssignmentCatalogs } from "../assignments/useAssignmentCatalogs";
import { appointmentAssignmentLabel } from "../assignments/assignmentLabels";
import type { RescheduleAppointmentRequest } from "./appointmentTypes";


function todayInTimeZone(
    timeZone: string
) {

    return dateKeyInTimeZone(
        new Date().toISOString(),
        timeZone
    );
}


function formatDay(
    date: string
) {
    const [
        year,
        month,
        day,
    ] =
        date
            .split("-")
            .map(Number);

    const value =
        new Date(
            Date.UTC(
                year,
                month - 1,
                day
            )
        );

    const weekday =
        new Intl.DateTimeFormat(
            "en-US",
            {
                weekday: "short",
                timeZone: "UTC",
            }
        ).format(value);

    return `${weekday} ${day}`;
}


function serviceTone(
    serviceId: string
) {

    const tones = [
        "sage",
        "sky",
        "lilac",
        "butter",
        "blush",
    ];


    let hash =
        0;


    for (
        let index = 0;
        index < serviceId.length;
        index++
    ) {
        hash =
            (
                hash
                +
                serviceId.charCodeAt(
                    index
                )
            )
            %
            10000;
    }


    return tones[
        hash % tones.length
    ];
}


function statusLabel(
    status:
        AppointmentStatus
) {

    return status
        .replace(
            "_",
            " "
        );
}


export default function AppointmentsPage() {

    const queryClient =
        useQueryClient();
    const catalogs = useAssignmentCatalogs();
    const [staffFilter, setStaffFilter] = useState("");
    const [locationFilter, setLocationFilter] = useState("");


    const settingsQuery =
        useTenantSettings();


    const [calendarHeight, setCalendarHeight] = useState(480);
    const calendarRef = useCallback((node: HTMLDivElement | null) => {
        if (!node) return;
        const measure = () => setCalendarHeight(Math.max(1, node.clientHeight - 64));
        measure();
        const observer = new ResizeObserver(measure);
        observer.observe(node);
        return () => observer.disconnect();
    }, []);

    const timeZone =
        settingsQuery.data
            ?.timeZone;


    const initialToday =
        timeZone
            ?
            todayInTimeZone(
                timeZone
            )
            :
            new Date()
                .toISOString()
                .slice(
                    0,
                    10
                );


    const [
        selectedDate,
        setSelectedDate,
    ] =
        useState(
            initialToday
        );


    const [
        creating,
        setCreating,
    ] =
        useState(false);


    const [
        selectedAppointment,
        setSelectedAppointment,
    ] =
        useState<
            AppointmentCalendarItem
            |
            null
        >(null);


    const [
        reschedulingAppointment,
        setReschedulingAppointment,
    ] =
        useState<
            AppointmentCalendarItem
            |
            null
        >(null);


    const [
        overlapAppointments,
        setOverlapAppointments,
    ] =
        useState<
            AppointmentCalendarItem[]
            |
            null
        >(null);

const [
        serviceFilter,
        setServiceFilter,
    ] =
        useState("");


const [
        statusFilter,
        setStatusFilter,
    ] =
        useState<
            AppointmentStatus
            |
            ""
        >("");


    const weekStart =
        startOfWeek(
            selectedDate, settingsQuery.data?.weekStartsOn ?? 1
        );


    const weekEnd =
        addDays(
            weekStart,
            6
        );


    const days =
        useMemo(
            () =>
                Array.from(
                    {
                        length: 7,
                    },
                    (
                        _,
                        index
                    ) =>
                        addDays(
                            weekStart,
                            index
                        )
                ),
            [
                weekStart,
            ]
        );





    const servicesQuery =
        useQuery({
            queryKey:
                ["services"],

            queryFn:
                getServices,
        });


    const appointmentsQuery =
        useQuery({

            queryKey: [
                "appointments",
                weekStart,
                weekEnd,
                staffFilter,
                locationFilter,
                serviceFilter,
                statusFilter,
            ],

            queryFn: () =>
                getAppointments(
                    weekStart,
                    weekEnd,
                    {
                        staffId: staffFilter || undefined,
                        locationId: locationFilter || undefined,

                        serviceId:
                            serviceFilter
                            ||
                            undefined,

                        status:
                            statusFilter
                            ||
                            undefined,
                    }
                ),
        });


    const layouts =
        useMemo(
            () => {

                if (
                    !timeZone
                    ||
                    !appointmentsQuery.data
                ) {
                    return {};
                }


                return Object.fromEntries(
                    days.map(
                        day => {

                            const appointments =
                                appointmentsQuery
                                    .data
                                    .filter(
                                        appointment =>
                                            dateKeyInTimeZone(
                                                appointment.startAt,
                                                timeZone
                                            )
                                            ===
                                            day
                                    );


                            return [
                                day,
                                layoutDayAppointments(
                                    appointments,
                                    timeZone
                                ),
                            ];
                        }
                    )
                );

            },
            [
                appointmentsQuery.data,
                days,
                timeZone,
            ]
        );


    async function refreshAppointments() {

        await Promise.all([
            queryClient
                .invalidateQueries({
                    queryKey: [
                        "appointments",
                    ],
                }),

            queryClient
                .invalidateQueries({
                    queryKey: [
                        "availability",
                    ],
                }),

            queryClient
                .invalidateQueries({
                    queryKey: [
                        "dashboard-summary",
                    ],
                }),
        ]);
    }


    const createMutation =
        useMutation({

            mutationFn:
                createAppointment,

            onSuccess:
                async () => {

                    await refreshAppointments();

                    setCreating(
                        false
                    );
                },
        });


    const cancelMutation =
        useMutation({

            mutationFn:
                cancelAppointment,

            onSuccess:
                async () => {

                    await refreshAppointments();

                    setSelectedAppointment(
                        null
                    );
                },
        });


    const statusMutation =
        useMutation({

            mutationFn: ({
                id,
                status,
            }: {
                id:
                    string;

                status:
                    AppointmentStatus;
            }) =>
                updateAppointmentStatus(
                    id,
                    status
                ),

            onSuccess:
                async () => {

                    await refreshAppointments();

                    setSelectedAppointment(
                        null
                    );
                },
        });


    const rescheduleMutation =
        useMutation({

            mutationFn: ({ appointmentId, request }: { appointmentId: string; request: RescheduleAppointmentRequest }) =>
                rescheduleAppointment(appointmentId, request),

            onSuccess:
                async () => {

                    await refreshAppointments();

                    setReschedulingAppointment(
                        null
                    );

                    setSelectedAppointment(
                        null
                    );
                },
        });


    const positioned = Object.values(layouts).flatMap(layout => layout.appointments);
    // Expand beyond configured hours when existing bookings would otherwise be hidden.
    const DAY_START = Math.max(0, Math.min((settingsQuery.data?.calendarStartHour ?? 8) * 60,
        ...positioned.map(item => Math.floor(item.startMinute / 60) * 60)));
    const DAY_END = Math.min(1440, Math.max((settingsQuery.data?.calendarEndHour ?? 18) * 60,
        ...positioned.map(item => Math.ceil(item.endMinute / 60) * 60)));
    const DAY_HEIGHT = calendarHeight;
    const PIXELS_PER_MINUTE = DAY_HEIGHT / (DAY_END - DAY_START);

    if (
        catalogs.isPending || settingsQuery.isPending
        ||
        servicesQuery.isPending
    ) {

        return (
            <p>
                Loading calendar...
            </p>
        );
    }


    if (
        catalogs.error || settingsQuery.isError
        ||
        servicesQuery.isError
    ) {

        return (
            <p>
                Could not load calendar data.
            </p>
        );
    }


    const tenantTimeZone =
        settingsQuery
            .data
            .timeZone;


    const today =
        todayInTimeZone(
            tenantTimeZone
        );


    const hours =
        Array.from(
            {
                length:
                    (
                        DAY_END
                        -
                        DAY_START
                    )
                    /
                    60
                    +
                    1,
            },
            (
                _,
                index
            ) =>
                DAY_START
                +
                index * 60
        );


    return (
        <div className="calendar-page">

            <div
                className={
                    "calendar-page-header"
                }
            >

                <div
                    className={
                        "calendar-header-main"
                    }
                >

                    <div
                        className={
                            "calendar-title-group"
                        }
                    >

                        <div>

                            <p
                                className={
                                    "page-eyebrow"
                                }
                            >
                                {
                                    weekStart
                                }
                                {" — "}
                                {
                                    weekEnd
                                }
                            </p>


                            <h1
                                className={
                                    "page-title"
                                }
                            >
                                Appointments
                            </h1>


                            <p
                                className={
                                    "page-description"
                                }
                            >
                                Weekly view ·{" "}
                                {
                                    tenantTimeZone
                                }
                            </p>

                        </div>


                        <div
                            className={
                                "calendar-title-icon"
                            }
                        >
                            <CalendarDays
                                size={23}
                            />
                        </div>

                    </div>


                    <button
                        className={
                            "button button-primary"
                        }

                        onClick={() => {

                            setSelectedAppointment(
                                null
                            );

                            setCreating(
                                true
                            );
                        }}
                    >
                        <Plus
                            size={18}
                        />

                        New appointment
                    </button>

                </div>


                <div
                    className={
                        "calendar-toolbar"
                    }
                >

                    <div
                        className={
                            "calendar-navigation"
                        }
                    >

                        <button
                            className={
                                "button button-secondary"
                            }

                            onClick={() =>
                                setSelectedDate(
                                    addDays(
                                        selectedDate,
                                        -7
                                    )
                                )
                            }
                        >
                            <ChevronLeft
                                size={17}
                            />

                            Previous
                        </button>


                        <button
                            className={
                                "button button-secondary"
                            }

                            onClick={() =>
                                setSelectedDate(
                                    today
                                )
                            }
                        >
                            Today
                        </button>


                        <button
                            className={
                                "button button-secondary"
                            }

                            onClick={() =>
                                setSelectedDate(
                                    addDays(
                                        selectedDate,
                                        7
                                    )
                                )
                            }
                        >
                            Next

                            <ChevronRight
                                size={17}
                            />
                        </button>

                    </div>


                    <div className="calendar-filters">
                        <SearchSelect className="calendar-filter" value={staffFilter} onChange={setStaffFilter} ariaLabel="Filter staff" options={[{ value: "", label: "All staff" }, ...catalogs.data.staff.map(member => ({ value: member.id, label: member.name, description: member.active ? "Active staff member" : "Inactive staff member" }))]} />
                        <SearchSelect className="calendar-filter" value={locationFilter} onChange={setLocationFilter} ariaLabel="Filter location" options={[{ value: "", label: "All locations" }, ...catalogs.data.locations.map(location => ({ value: location.id, label: location.name }))]} />
                        <SearchSelect className="calendar-filter" value={serviceFilter} onChange={setServiceFilter} ariaLabel="Filter service" options={[{ value: "", label: "All services" }, ...servicesQuery.data.map(service => ({ value: service.id, label: service.name }))]} />
                        <SearchSelect className="calendar-filter" value={statusFilter} onChange={value => setStatusFilter(value as AppointmentStatus | "")} ariaLabel="Filter status" searchable={false} options={[{ value: "", label: "All statuses" }, ...(["PENDING", "CONFIRMED", "CANCELLED", "COMPLETED", "NO_SHOW"] as AppointmentStatus[]).map(status => ({ value: status, label: statusLabel(status) }))]} />
                    </div>

                </div>

            </div>


            {appointmentsQuery.isPending && (
                <p>
                    Loading appointments...
                </p>
            )}


            {appointmentsQuery.isError && (
                <p>
                    {
                        appointmentsQuery
                            .error
                            .message
                    }
                </p>
            )}


            {appointmentsQuery.data && (

                <div
                    className={
                        "calendar-shell card"
                    }
                    ref={calendarRef}
                >

                    <div
                        className={
                            "calendar-grid"
                        }
                    >

                        <div
                            className={
                                "calendar-time-header"
                            }
                        />


                        {days.map(
                            day => {

                                const layout =
                                    layouts[
                                        day
                                    ];


                                const count =
                                    layout
                                        ?.appointments
                                        .length
                                    ??
                                    0;


                                return (
                                    <div
                                        key={
                                            day
                                        }

                                        className={
                                            day
                                            ===
                                            today

                                                ?
                                                "calendar-day-header today"

                                                :
                                                "calendar-day-header"
                                        }
                                    >

                                        <div
                                            className={
                                                "calendar-day-name"
                                            }
                                        >
                                            {
                                                formatDay(
                                                    day
                                                )
                                            }
                                        </div>


                                        <div
                                            className={
                                                "calendar-day-count"
                                            }
                                        >
                                            {
                                                count
                                            }{" "}

                                            {
                                                count === 1
                                                    ?
                                                    "appointment"
                                                    :
                                                    "appointments"
                                            }
                                        </div>

                                    </div>
                                );
                            }
                        )}


                        <div
                            className={
                                "calendar-time-column"
                            }

                            style={{
                                height:
                                    DAY_HEIGHT,
                            }}
                        >

                            {hours.map(
                                minute => {

                                    const top =
                                        (
                                            minute
                                            -
                                            DAY_START
                                        )
                                        *
                                        PIXELS_PER_MINUTE;


                                    const hour =
                                        Math.floor(
                                            minute
                                            /
                                            60
                                        );


                                    return (
                                        <div
                                            key={
                                                minute
                                            }

                                            className={
                                                "calendar-hour-label"
                                            }

                                            style={{
                                                top,
                                            }}
                                        >
                                            {
                                                String(
                                                    hour
                                                )
                                                    .padStart(
                                                        2,
                                                        "0"
                                                    )
                                            }
                                            :00
                                        </div>
                                    );
                                }
                            )}

                        </div>


                        {days.map(
                            day => {

                                const layout =
                                    layouts[
                                        day
                                    ];


                                const visibleAppointments =
                                    layout
                                        ?.appointments
                                        ??
                                        [];


                                const overlapClusters =
                                    layout
                                        ?.clusters
                                        .filter(
                                            cluster =>
                                                cluster.laneCount
                                                >
                                                2
                                        )
                                    ??
                                    [];


                                return (
                                    <div
                                        key={
                                            day
                                        }

                                        className={
                                            day
                                            ===
                                            today

                                                ?
                                                "calendar-day-column today"

                                                :
                                                "calendar-day-column"
                                        }

                                        style={{
                                            height:
                                                DAY_HEIGHT,

                                            backgroundSize:
                                                `100% ${60 * PIXELS_PER_MINUTE}px`,
                                        }}
                                    >

                                        {hours.map(
                                            minute => (

                                                <div
                                                    key={
                                                        minute
                                                    }

                                                    className={
                                                        "calendar-hour-line"
                                                    }

                                                    style={{
                                                        top:
                                                            (
                                                                minute
                                                                -
                                                                DAY_START
                                                            )
                                                            *
                                                            PIXELS_PER_MINUTE,
                                                    }}
                                                />

                                            )
                                        )}


                                        {
                                            visibleAppointments
                                                .filter(
                                                    positioned => {

                                                        /*
                                                         * If there are more
                                                         * than two simultaneous
                                                         * lanes, keep the first
                                                         * two readable and move
                                                         * the rest into the
                                                         * +N overlap control.
                                                         */
                                                        return (
                                                            positioned
                                                                .laneCount
                                                            <=
                                                            2
                                                            ||
                                                            positioned
                                                                .lane
                                                            <
                                                            2
                                                        );
                                                    }
                                                )
                                                .filter(
                                                    positioned =>
                                                        positioned.endMinute
                                                        >
                                                        DAY_START
                                                        &&
                                                        positioned.startMinute
                                                        <
                                                        DAY_END
                                                )
                                                .map(
                                                    positioned => {

                                                        const appointment =
                                                            positioned
                                                                .appointment;


                                                        const laneCount =
                                                            Math.min(
                                                                positioned
                                                                    .laneCount,

                                                                2
                                                            );


                                                        const width =
                                                            100
                                                            /
                                                            laneCount;


                                                        const left =
                                                            positioned
                                                                .lane
                                                            *
                                                            width;


                                                        const start =
                                                            Math.max(
                                                                positioned
                                                                    .startMinute,

                                                                DAY_START
                                                            );


                                                        const end =
                                                            Math.min(
                                                                positioned
                                                                    .endMinute,

                                                                DAY_END
                                                            );


                                                        const top =
                                                            (
                                                                start
                                                                -
                                                                DAY_START
                                                            )
                                                            *
                                                            PIXELS_PER_MINUTE;


                                                        const height =
                                                            Math.max(
                                                                4,

                                                                (
                                                                    end
                                                                    -
                                                                    start
                                                                )
                                                                *
                                                                PIXELS_PER_MINUTE
                                                                -
                                                                4
                                                            );


                                                        const tone =
                                                            serviceTone(
                                                                appointment
                                                                    .serviceId
                                                            );

                                                        const primaryLabel = appointment.customerName;
                                                        const secondaryLabel = `${appointment.serviceName} · ${appointmentAssignmentLabel(appointment)}`;

                                                        const compact =
                                                            height < 54;

                                                        const ultraCompact =
                                                            height < 42;



                                                        return (
                                                            <button
                                                                key={
                                                                    appointment
                                                                        .id
                                                                }

                                                                data-tooltip={`${appointment.customerName}\n${appointment.serviceName}\n${formatTime(appointment.startAt, tenantTimeZone)}–${formatTime(appointment.endAt, tenantTimeZone)} · ${appointmentAssignmentLabel(appointment)}\n${statusLabel(appointment.status)}`}
                                                                className={
                                                                    `calendar-appointment service-tone-${tone}`
                                                                }

                                                                style={{
                                                                    top,

                                                                    height,

                                                                    left:
                                                                        `calc(${left}% + 3px)`,

                                                                    width:
                                                                        `calc(${width}% - 6px)`,
                                                                }}

                                                                onClick={() =>
                                                                    setSelectedAppointment(
                                                                        appointment
                                                                    )
                                                                }
                                                            >
                                                                <span className="calendar-appointment-time">
                                                                    {formatTime(
                                                                        appointment.startAt,
                                                                        tenantTimeZone
                                                                    )}
                                                                    {" – "}
                                                                    {formatTime(
                                                                        appointment.endAt,
                                                                        tenantTimeZone
                                                                    )}
                                                                </span>

                                                                {!ultraCompact && (
                                                                    <span className="calendar-appointment-primary">
                                                                        {primaryLabel}
                                                                    </span>
                                                                )}

                                                                {!compact && secondaryLabel && (
                                                                    <span className="calendar-appointment-secondary">
                                                                        {secondaryLabel}
                                                                    </span>
                                                                )}

                                                            </button>
                                                        );
                                                    }
                                                )
                                        }


                                        {overlapClusters.map(
                                            cluster => {

                                                const hiddenCount =
                                                    Math.max(
                                                        0,

                                                        cluster
                                                            .laneCount
                                                        -
                                                        2
                                                    );


                                                const top =
                                                    Math.max(
                                                        2,

                                                        (
                                                            cluster
                                                                .startMinute
                                                            -
                                                            DAY_START
                                                        )
                                                        *
                                                        PIXELS_PER_MINUTE
                                                        -
                                                        27
                                                    );


                                                return (
                                                    <button
                                                        key={
                                                            cluster.id
                                                        }

                                                        className={
                                                            "calendar-overlap-chip"
                                                        }

                                                        style={{
                                                            top,
                                                        }}

                                                        onClick={() =>
                                                            setOverlapAppointments(
                                                                cluster
                                                                    .appointments
                                                            )
                                                        }
                                                    >
                                                        <Users
                                                            size={13}
                                                        />

                                                        +
                                                        {
                                                            hiddenCount
                                                        }{" "}

                                                        more
                                                    </button>
                                                );
                                            }
                                        )}


                                        {
                                            visibleAppointments
                                                .length
                                            ===
                                            0
                                            && (
                                                <div
                                                    className={
                                                        "calendar-empty-day"
                                                    }
                                                >
                                                    No appointments
                                                </div>
                                            )
                                        }

                                    </div>
                                );
                            }
                        )}

                    </div>

                </div>
            )}


            {creating && (

                <div
                    className={
                        "calendar-modal-backdrop"
                    }

                    onMouseDown={() =>
                        setCreating(
                            false
                        )
                    }
                >

                    <div
                        className={
                            "calendar-modal"
                        }

                        onMouseDown={
                            event =>
                                event
                                    .stopPropagation()
                        }
                    >

                        <BookingForm
                            onSubmit={
                                async (
                                    request:
                                        CreateAppointmentRequest
                                ) => {

                                    await createMutation
                                        .mutateAsync(
                                            request
                                        );
                                }
                            }

                            onCancel={() =>
                                setCreating(
                                    false
                                )
                            }
                        />

                    </div>

                </div>
            )}


            {reschedulingAppointment && (

                <div
                    className={
                        "calendar-modal-backdrop"
                    }

                    onMouseDown={() =>
                        setReschedulingAppointment(
                            null
                        )
                    }
                >

                    <div
                        className={
                            "calendar-modal"
                        }

                        onMouseDown={
                            event =>
                                event
                                    .stopPropagation()
                        }
                    >

                        <RescheduleForm
                            appointment={
                                reschedulingAppointment
                            }

                            timeZone={
                                tenantTimeZone
                            }

                            onCancel={() =>
                                setReschedulingAppointment(
                                    null
                                )
                            }

                            onSubmit={async request => {
                                await rescheduleMutation.mutateAsync({ appointmentId: reschedulingAppointment.id, request });
                            }}
                        />

                    </div>

                </div>
            )}


            {selectedAppointment && <AppointmentDetails key={selectedAppointment.id} appointment={selectedAppointment} timeZone={tenantTimeZone} onClose={() => setSelectedAppointment(null)} onReschedule={() => { setReschedulingAppointment(selectedAppointment); setSelectedAppointment(null); }} onCancel={() => cancelMutation.mutateAsync(selectedAppointment.id)} onStatus={status => statusMutation.mutateAsync({ id: selectedAppointment.id, status })} />}

            {overlapAppointments && (

                <div
                    className={
                        "calendar-modal-backdrop"
                    }

                    onMouseDown={() =>
                        setOverlapAppointments(
                            null
                        )
                    }
                >

                    <div
                        className={
                            "calendar-modal"
                        }

                        onMouseDown={
                            event =>
                                event
                                    .stopPropagation()
                        }
                    >

                        <div
                            className={
                                "drawer-header"
                            }
                        >

                            <div>
                                <p
                                    className={
                                        "page-eyebrow"
                                    }
                                >
                                    Overlapping appointments
                                </p>

                                <h2
                                    className={
                                        "dashboard-panel-title"
                                    }
                                >
                                    {
                                        overlapAppointments
                                            .length
                                    }{" "}
                                    appointments
                                </h2>
                            </div>


                            <button
                                className={
                                    "drawer-close"
                                }

                                onClick={() =>
                                    setOverlapAppointments(
                                        null
                                    )
                                }
                            >
                                <X
                                    size={18}
                                />
                            </button>

                        </div>


                        <div
                            className={
                                "overlap-list"
                            }
                        >

                            {
                                overlapAppointments
                                    .map(
                                        appointment => (

                                            <button
                                                key={
                                                    appointment.id
                                                }

                                                className={
                                                    "overlap-list-item"
                                                }

                                                onClick={() => {

                                                    setOverlapAppointments(
                                                        null
                                                    );

                                                    setSelectedAppointment(
                                                        appointment
                                                    );
                                                }}
                                            >

                                                <div>

                                                    <div
                                                        className={
                                                            "overlap-list-main"
                                                        }
                                                    >
                                                        {
                                                            appointment
                                                                .customerName
                                                        }
                                                    </div>


                                                    <div
                                                        className={
                                                            "overlap-list-detail"
                                                        }
                                                    >
                                                        {
                                                            appointment
                                                                .serviceName
                                                        }

                                                        {" · "}

                                                        {
                                                            appointmentAssignmentLabel(appointment)
                                                        }
                                                    </div>

                                                </div>


                                                <strong>
                                                    {
                                                        formatTime(
                                                            appointment
                                                                .startAt,

                                                            tenantTimeZone
                                                        )
                                                    }
                                                </strong>

                                            </button>
                                        )
                                    )
                            }

                        </div>

                    </div>

                </div>
            )}

        </div>
    );
}