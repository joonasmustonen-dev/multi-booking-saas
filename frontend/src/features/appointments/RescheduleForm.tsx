import BookingEditor from "./BookingEditor";
import type { AppointmentCalendarItem, RescheduleAppointmentRequest } from "./appointmentTypes";
interface Props { appointment: AppointmentCalendarItem; timeZone: string; onSubmit: (request: RescheduleAppointmentRequest) => Promise<void>; onCancel: () => void }
export default function RescheduleForm({ appointment, onSubmit, onCancel }: Props) {
    return <BookingEditor appointment={appointment} onCancel={onCancel} onSubmit={request => onSubmit({ staffId: request.staffId, locationId: request.locationId, resourceId: request.resourceId, startAt: request.startAt })} />;
}