import { ArrowRight, CalendarDays, Clock3, Plus, Users } from "lucide-react";

import { useQuery } from "@tanstack/react-query";

import { useNavigate } from "react-router-dom";

import keycloak from "../../auth/keycloak";

import { formatTime } from "../settings/timeFormat";

import { getDashboardSummary } from "./dashboardApi";

import type { AppointmentStatus } from "../appointments/appointmentTypes";

import "./DashboardPage.css";
import { entityLabel } from "../assignments/entityLabels";
import { useState } from "react";
import SearchSelect from "../../components/SearchSelect";
import { appointmentAssignmentLabel } from "../assignments/assignmentLabels";

function statusClass(status: AppointmentStatus) {
    switch (status) {
        case "CONFIRMED":
            return "status-pill-confirmed";

        case "PENDING":
            return "status-pill-pending";

        case "COMPLETED":
            return "status-pill-completed";

        case "NO_SHOW":
            return "status-pill-no-show";

        case "CANCELLED":
            return "status-pill-cancelled";
    }
}

function initials(value: string) {
    return value
        .split(/\s+/)
        .filter(Boolean)
        .map(part => part.charAt(0))
        .join("")
        .slice(0, 2)
        .toUpperCase();
}

export default function DashboardPage() {
    const [staffId, setStaffId] = useState("");

    const navigate = useNavigate();

    const summaryQuery = useQuery({
        queryKey: ["dashboard-summary"],

        queryFn: getDashboardSummary
    });

    const username = keycloak.tokenParsed?.preferred_username as
        string | undefined;

    const firstName = username
        ? username.charAt(0).toUpperCase() + username.slice(1)
        : "there";

    if (summaryQuery.isPending) {
        return <p>Loading dashboard...</p>;
    }

    if (summaryQuery.isError) {
        return (
            <div className="card dashboard-panel">
                <h2>Could not load dashboard</h2>

                <p>{summaryQuery.error.message}</p>
            </div>
        );
    }

    const summary = summaryQuery.data;

    const todaysAppointments = summary.todaysAppointments.filter(
        appointment =>
            !staffId ||
            (staffId === "unassigned"
                ? !appointment.staffId
                : appointment.staffId === staffId)
    );
    const staffOptions = [
        ...new Map([
            ...summary.team.map(
                member => [member.staffId, member.name] as const
            ),
            ...summary.todaysAppointments
                .filter(appointment => appointment.staffId)
                .map(
                    appointment =>
                        [
                            appointment.staffId!,
                            appointment.staffName || "Staff member"
                        ] as const
                )
        ]).entries()
    ].map(([value, label]) => ({
        value,
        label: entityLabel(
            { id: value, name: label },
            summary.team.map(member => ({
                id: member.staffId,
                name: member.name
            }))
        )
    }));

    const maxDailyBookings = Math.max(
        1,
        ...summary.dailyBookings.map(item => item.count)
    );

    const maxStatusCount = Math.max(
        1,
        ...summary.statusCounts.map(item => item.count)
    );

    const todayLabel = new Intl.DateTimeFormat(undefined, {
        weekday: "short",
        day: "numeric",
        month: "short",
        year: "numeric",
        timeZone: summary.timeZone
    })
        .format(new Date())
        .toUpperCase();

    return (
        <div>
            <div className="page-header">
                <div>
                    <p className="page-eyebrow">{todayLabel}</p>

                    <h1 className="page-title">Good morning, {firstName}</h1>

                    <p className="page-description">
                        Here&apos;s what&apos;s happening this week.
                    </p>
                </div>

                <button
                    className={"button button-primary"}

                    onClick={() => navigate("/appointments?openCreate=1")}
                >
                    <Plus size={18} />
                    New appointment
                </button>
            </div>

            <section className={"dashboard-metrics"}>
                <article className={"card metric-card"}>
                    <div className={"metric-icon metric-icon-sage"}>
                        <CalendarDays size={23} />
                    </div>

                    <div>
                        <p className="metric-value">{summary.todaysBookings}</p>

                        <p className="metric-label">Today&apos;s bookings</p>
                    </div>

                    <span className="metric-note">Today</span>
                </article>

                <article className={"card metric-card"}>
                    <div className={"metric-icon metric-icon-sky"}>
                        <Clock3 size={23} />
                    </div>

                    <div>
                        <p className="metric-value">{summary.openSlots}</p>

                        <p className="metric-label">Open slots</p>
                    </div>

                    <span className="metric-note">Remaining</span>
                </article>

                <article className={"card metric-card"}>
                    <div className={"metric-icon metric-icon-lilac"}>
                        <Users size={23} />
                    </div>

                    <div>
                        <p className="metric-value">{summary.customers}</p>

                        <p className="metric-label">Customers</p>
                    </div>

                    <span className="metric-note">Total</span>
                </article>
            </section>

            <section className={"dashboard-insights"}>
                <article className={"card dashboard-panel"}>
                    <div className={"dashboard-panel-header"}>
                        <div>
                            <h2 className={"dashboard-panel-title"}>
                                Bookings this week
                            </h2>

                            <div className={"dashboard-big-number"}>
                                {summary.bookingsThisWeek}
                            </div>

                            <div className={"dashboard-small-label"}>
                                Total bookings
                            </div>
                        </div>
                    </div>

                    <div className="week-chart">
                        {summary.dailyBookings.map(item => {
                            const height = Math.max(
                                5,

                                (item.count / maxDailyBookings) * 100
                            );

                            const day = new Intl.DateTimeFormat(undefined, {
                                weekday: "short",

                                timeZone: "UTC"
                            }).format(new Date(`${item.date}T12:00:00Z`));

                            return (
                                <div
                                    className={"week-chart-column"}

                                    key={item.date}
                                >
                                    <div className={"week-chart-bar-area"}>
                                        <div
                                            className={"week-chart-bar"}

                                            data-tooltip={`${item.count} bookings`}

                                            style={{
                                                height: `${height}%`
                                            }}
                                        />
                                    </div>

                                    <span className={"week-chart-day"}>
                                        {day}
                                    </span>
                                </div>
                            );
                        })}
                    </div>
                </article>

                <article className={"card dashboard-panel"}>
                    <div className={"dashboard-panel-header"}>
                        <h2 className={"dashboard-panel-title"}>Week status</h2>
                    </div>

                    <div className="status-summary">
                        {summary.statusCounts.map(item => {
                            const width = (item.count / maxStatusCount) * 100;

                            const cssStatus = item.status
                                .toLowerCase()
                                .replace("_", "-");

                            return (
                                <div
                                    className={"status-row"}

                                    key={item.status}
                                >
                                    <span className={"status-label"}>
                                        {item.status.replace("_", " ")}
                                    </span>

                                    <div className={"status-track"}>
                                        <div
                                            className={`status-fill status-${cssStatus}`}

                                            style={{
                                                width: `${width}%`
                                            }}
                                        />
                                    </div>

                                    <span className={"status-count"}>
                                        {item.count}
                                    </span>
                                </div>
                            );
                        })}
                    </div>
                </article>
            </section>

            <section className={"dashboard-lower"}>
                <article className={"card dashboard-panel"}>
                    <div className={"dashboard-panel-header"}>
                        <h2 className={"dashboard-panel-title"}>Today</h2>

                        <button
                            className={"button button-secondary"}

                            onClick={() => navigate("/appointments")}
                        >
                            View calendar
                            <ArrowRight size={16} />
                        </button>
                    </div>

                    <div className="dashboard-today-filter">
                        <SearchSelect
                            value={staffId}
                            onChange={setStaffId}
                            ariaLabel="Filter today by staff"
                            options={[
                                { value: "", label: "All staff" },
                                ...staffOptions,
                                { value: "unassigned", label: "Unassigned" }
                            ]}
                        />
                    </div>
                    {todaysAppointments.length === 0 ? (
                        <div className={"dashboard-empty"}>
                            {staffId
                                ? "No appointments for this staff selection today."
                                : "No appointments today."}
                        </div>
                    ) : (
                        <div className="today-list">
                            {todaysAppointments.map(appointment => (
                                <div
                                    className={"today-row"}

                                    key={appointment.id}
                                >
                                    <div className={"today-time"}>
                                        {formatTime(
                                            appointment.startAt,

                                            summary.timeZone
                                        )}
                                    </div>

                                    <div>
                                        <div className={"today-customer"}>
                                            {appointment.customerName}
                                        </div>

                                        <div className={"today-detail"}>
                                            {appointment.serviceName}

                                            {" · "}

                                            {appointmentAssignmentLabel(
                                                appointment
                                            )}
                                        </div>
                                    </div>

                                    <span
                                        className={`status-pill ${statusClass(
                                            appointment.status
                                        )}`}
                                    >
                                        {appointment.status.replace("_", " ")}
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}
                </article>

                <article className={"card dashboard-panel"}>
                    <div className={"dashboard-panel-header"}>
                        <h2 className={"dashboard-panel-title"}>
                            Today&apos;s team
                        </h2>
                    </div>

                    {summary.team.length === 0 ? (
                        <div className={"dashboard-empty"}>
                            No active staff members.
                        </div>
                    ) : (
                        <div className="team-list">
                            {summary.team.map(member => (
                                <div
                                    className={"team-row"}

                                    key={member.staffId}
                                >
                                    <div className={"team-avatar"}>
                                        {initials(member.name)}
                                    </div>

                                    <span className={"team-name"}>
                                        {entityLabel(
                                            {
                                                id: member.staffId,
                                                name: member.name
                                            },
                                            summary.team.map(item => ({
                                                id: item.staffId,
                                                name: item.name
                                            }))
                                        )}
                                    </span>

                                    <span className={"team-bookings"}>
                                        {member.todaysBookings} bookings
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}
                </article>

                <div className="dashboard-right">
                    <article className="card dashboard-panel">
                        <div className="dashboard-panel-header">
                            <h2 className="dashboard-panel-title">
                                Popular services
                            </h2>
                        </div>

                        {summary.popularServices.length === 0 ? (
                            <div className="dashboard-empty">
                                No bookings this week.
                            </div>
                        ) : (
                            <div className="popular-list">
                                {summary.popularServices.map(
                                    (service, index) => (
                                        <div
                                            className="popular-row"
                                            key={service.serviceId}
                                        >
                                            <div className="popular-rank">
                                                {index + 1}
                                            </div>

                                            <span className="popular-name">
                                                {service.name}
                                            </span>

                                            <span className="popular-count">
                                                {service.bookings} bookings
                                            </span>
                                        </div>
                                    )
                                )}
                            </div>
                        )}
                    </article>
                </div>
            </section>
        </div>
    );
}
