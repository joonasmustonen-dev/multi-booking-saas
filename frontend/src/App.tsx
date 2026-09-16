import { Navigate, Route, Routes } from "react-router-dom";

import AppLayout from "./components/AppLayout";
import DashboardPage from "./features/dashboard/DashboardPage";
import CustomersPage from "./features/customers/CustomersPage";
import ResourcesPage from "./features/resources/ResourcesPage";
import ServicesPage from "./features/services/ServicesPage";
import AvailabilityRedirect from "./features/availability/AvailabilityRedirect";
import StaffPage from "./features/staff/StaffPage";
import AppointmentsPage from "./features/appointments/AppointmentsPage";
import SettingsPage from "./features/settings/SettingsPage";

import LocationsPage from "./features/locations/LocationsPage";
import LandingPage from "./pages/LandingPage";

export default function App() {
    return (
        <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route element={<AppLayout />}>
                <Route path="/app" element={<DashboardPage />} />

                <Route path="/customers" element={<CustomersPage />} />
                <Route path="/locations" element={<LocationsPage />} />
                <Route path="/resources" element={<ResourcesPage />} />
                <Route path="/services" element={<ServicesPage />} />
                <Route
                    path="/availability"
                    element={<AvailabilityRedirect />}
                />
                <Route path="/appointments" element={<AppointmentsPage />} />
                <Route path="/staff" element={<StaffPage />} />
                <Route path="/settings" element={<SettingsPage />} />
                <Route path="*" element={<Navigate to="/app" replace />} />
            </Route>
        </Routes>
    );
}
