import { addDays, startOfWeek } from "../appointments/dateUtils";
import type { StaffShift } from "./staffApi";
export const nextStaffWeek = (today: string) =>
    addDays(startOfWeek(today, 1), 7);
export function copyShiftsToWeek(
    shifts: StaffShift[],
    from: string,
    to: string
) {
    return shifts.map(shift => {
        const offset = Math.round(
            (Date.parse(shift.date) - Date.parse(from)) / 86400000
        );
        return { ...shift, date: addDays(to, offset) };
    });
}
