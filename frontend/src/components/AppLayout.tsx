import {
    Building2,
    CalendarDays,
    LayoutDashboard,
    LogOut,
    Menu,
    Package,
    Settings,
    Tag,
    Users,
    X
} from "lucide-react";

import { useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";

import keycloak from "../auth/keycloak";

import { useTenantSettings } from "../features/settings/useTenantSettings";

const navigation = [
    {
        to: "/app",
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
    const location = useLocation();
    const calendarPage = location.pathname === "/appointments";
    const dashboardPage = location.pathname === "/app";
    const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
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
            <aside
                className={`sidebar${mobileNavigationOpen ? " mobile-open" : ""}`}
                aria-label="Workspace navigation"
            >
                <div className="sidebar-brand">
                    <div className={"sidebar-brand-mark"}>
                        M
                    </div>

                    <span className={"sidebar-brand-copy"}>
                        <strong className={"sidebar-brand-name"}>
                            MultiBooking
                        </strong>
                        <small>Workspace</small>
                    </span>
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
                            onClick={() => setMobileNavigationOpen(false)}
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

            <button
                type="button"
                className={`sidebar-scrim${mobileNavigationOpen ? " visible" : ""}`}
                aria-label="Close navigation"
                aria-hidden={!mobileNavigationOpen}
                tabIndex={mobileNavigationOpen ? 0 : -1}
                onClick={() => setMobileNavigationOpen(false)}
            />

            <div className="app-main">
                <header className="topbar">
                    <button
                        type="button"
                        className="mobile-menu-button"
                        aria-label={
                            mobileNavigationOpen
                                ? "Close navigation"
                                : "Open navigation"
                        }
                        aria-expanded={mobileNavigationOpen}
                        onClick={() =>
                            setMobileNavigationOpen(open => !open)
                        }
                    >
                        {mobileNavigationOpen ? (
                            <X size={20} />
                        ) : (
                            <Menu size={20} />
                        )}
                    </button>

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
