export interface Customer {
    id: string;
    firstName: string;
    lastName: string;
    email: string | null;
    phone: string | null;
    preferredStaffId?: string | null;
    createdAt: string;
}

export interface CreateCustomerRequest {
    firstName: string;
    lastName: string;
    email: string | null;
    phone: string | null;
    preferredStaffId?: string | null;
}

export interface UpdateCustomerRequest {
    firstName: string;
    lastName: string;
    email: string | null;
    phone: string | null;
    preferredStaffId?: string | null;
}
export interface CustomerActivity {
    customer: Customer; preferredStaffName: string | null;
    totalBookings: number; completedBookings: number; cancelledBookings: number; noShows: number;
    lastVisit: string | null;
    mostBookedServices: { serviceId: string; name: string; visits: number }[];
    bookings: { id: string; serviceId: string; serviceName: string; staffName: string | null;
        locationName: string | null; resourceName: string | null; startAt: string; endAt: string;
        status: import('../appointments/appointmentTypes').AppointmentStatus }[];
    page: number; size: number; totalElements: number; totalPages: number;
}
