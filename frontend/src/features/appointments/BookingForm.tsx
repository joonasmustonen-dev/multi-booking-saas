import BookingEditor from "./BookingEditor";
import type { CreateAppointmentRequest } from "./appointmentTypes";
export default function BookingForm(props: { onSubmit: (request: CreateAppointmentRequest) => Promise<void>; onCancel: () => void }) {
    return <BookingEditor {...props} />;
}