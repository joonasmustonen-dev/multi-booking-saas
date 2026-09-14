import {
    Building2,
    CalendarDays,
    LayoutDashboard,
    LogOut,
    Package,
    Settings,
    Tag,
    Users
} from "lucide-react";

import { NavLink, Outlet, useLocation } from "react-router-dom";

import keycloak from "../auth/keycloak";

import { useTenantSettings } from "../features/settings/useTenantSettings";

const navigation = [
    {
        to: "/",
        label: "Dashboard",
        icon: LayoutDashboard,
        end: true
    },
    {
        to: "/appointments",
        label: "Calendar",
        icon: CalendarDays
    },
    {
        to: "/customers",
        label: "Customers",
        icon: Users
    },
    {
        to: "/resources",
        label: "Resources",
        icon: Package
    },
    {
        to: "/services",
        label: "Services",
        icon: Tag
    },
    { to: "/locations", label: "Locations", icon: Building2 },
    {
        to: "/staff",
        label: "Staff",
        icon: Users
    },
    {
        to: "/settings",
        label: "Settings",
        icon: Settings
    }
];

import TooltipLayer from "./TooltipLayer";
export default function AppLayout() {
    const calendarPage = useLocation().pathname === "/appointments";
    const dashboardPage = useLocation().pathname === "/";
    const settingsQuery = useTenantSettings();

    const username = keycloak.tokenParsed?.preferred_username as
        string | undefined;

    const displayName = username ?? "User";

    const initial = displayName.charAt(0).toUpperCase();

    const timezone = settingsQuery.data?.timeZone ?? "Loading…";

    return (
        <div
            className={`app-shell${calendarPage ? " calendar-workspace" : ""}${dashboardPage ? " dashboard-workspace" : ""}`}
        >
            <aside className="sidebar">
                <div className="sidebar-brand">
                    <div className={"sidebar-brand-mark"}>
                        <CalendarDays size={19} strokeWidth={2} />
                    </div>

                    <span className={"sidebar-brand-name"}>BOOKING</span>
                </div>

                <nav className="sidebar-nav">
                    {navigation.map(({ to, label, icon: Icon, end }) => (
                        <NavLink
                            key={to}
                            to={to}
                            end={end}

                            className={({ isActive }) =>
                                isActive
                                    ? "sidebar-link active"
                                    : "sidebar-link"
                            }
                        >
                            <Icon size={19} strokeWidth={1.9} />

                            <span>{label}</span>
                        </NavLink>
                    ))}
                </nav>

                <div className="sidebar-footer">
                    <div className="sidebar-business">
                        <Building2 size={19} />

                        <span>
                            {settingsQuery.data?.businessName ??
                                "Booking workspace"}
                        </span>
                    </div>

                    <div className="sidebar-timezone">{timezone}</div>
                </div>
            </aside>

            <div className="app-main">
                <header className="topbar">
                    <div className={"topbar-profile"}>
                        <div className={"profile-avatar"}>{initial}</div>

                        <span className={"profile-name"}>{displayName}</span>

                        <button
                            className={"logout-button"}
                            aria-label="Logout"

                            data-tooltip="Logout"

                            onClick={() =>
                                keycloak.logout({
                                    redirectUri: window.location.origin
                                })
                            }
                        >
                            <LogOut size={18} />
                        </button>
                    </div>
                </header>

                <main className="page-content">
                    <Outlet />
                    <TooltipLayer />
                </main>
            </div>
        </div>
    );
}
