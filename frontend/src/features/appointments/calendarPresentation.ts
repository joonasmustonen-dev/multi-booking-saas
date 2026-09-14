import type { DayCalendarLayout, PositionedAppointment } from './calendarLayout';
export interface CalendarDisplayGroup { items: PositionedAppointment[]; top: number; height: number; grouped: boolean }
// Group bookings whose minimum readable cards collide, without changing booking times.
export function calendarDisplayGroups(layout: DayCalendarLayout, start: number, end: number, height: number, maxLanes = 2): CalendarDisplayGroup[] {
    const scale = height / Math.max(1, end - start); const minimum = Math.min(38, height);
    const groups: CalendarDisplayGroup[] = [];
    for (const cluster of layout.clusters) {
        const items = layout.appointments.filter(item => item.clusterId === cluster.id);
        const top = Math.max(0, Math.min(height - minimum, (cluster.startMinute - start) * scale));
        const bottom = Math.min(height, Math.max(top + minimum, (cluster.endMinute - start) * scale));
        const grouped = cluster.laneCount > maxLanes || (items.length > 1 && items.some(item => (item.endMinute - item.startMinute) * scale < minimum));
        const previous = groups.at(-1);
        if (previous && previous.top + previous.height > top) {
            previous.items.push(...items); previous.height = Math.max(previous.height, bottom - previous.top); previous.grouped = true;
        } else groups.push({ items, top, height: bottom - top, grouped });
    }
    return groups;
}
export function calendarTickMinutes(start: number, end: number, height: number) {
    const scale = height / Math.max(1, end - start);
    const step = [60, 120, 180, 240, 360, 720, 1440].find(value => value * scale >= 30) ?? 1440;
    const ticks = [start];
    for (let time = Math.ceil((start + 1) / step) * step; time < end; time += step) ticks.push(time);
    if (ticks.length > 1 && (end - ticks[ticks.length - 1]) * scale < 20) ticks.pop();
    ticks.push(end); return ticks;
}
