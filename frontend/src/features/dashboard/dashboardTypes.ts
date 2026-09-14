import type {
    AppointmentCalendarItem,
    AppointmentStatus
} from "../appointments/appointmentTypes";

export interface DashboardSummary {
    timeZone: string;
    today: string;

    todaysBookings: number;
    openSlots: number;
    customers: number;
    bookingsThisWeek: number;

    dailyBookings: DashboardDailyBooking[];

    todaysAppointments: AppointmentCalendarItem[];

    team: DashboardTeamMember[];

    popularServices: DashboardPopularService[];

    statusCounts: DashboardStatusCount[];
}

export interface DashboardDailyBooking {
    date: string;
    count: number;
}

export interface DashboardTeamMember {
    staffId: string;
    name: string;
    todaysBookings: number;
}

export interface DashboardPopularService {
    serviceId: string;
    name: string;
    bookings: number;
}

export interface DashboardStatusCount {
    status: AppointmentStatus;
    count: number;
}
