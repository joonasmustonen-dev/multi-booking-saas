import type { AvailabilityRule, DayOfWeek } from './availabilityTypes';
export const weekDays: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export type RecurringDraft = Pick<AvailabilityRule, 'dayOfWeek' | 'startTime' | 'endTime' | 'active'>;
export function toggleRecurringDay(rules: RecurringDraft[], day: DayOfWeek, active: boolean): RecurringDraft[] {
    if (!rules.some(rule => rule.dayOfWeek === day)) return [...rules, { dayOfWeek: day, startTime: '09:00', endTime: '17:00', active }];
    return rules.map(rule => rule.dayOfWeek === day ? { ...rule, active } : rule);
}
export function recurringWeekdaysPreset(): RecurringDraft[] {
    return weekDays.slice(0, 5).map(dayOfWeek => ({ dayOfWeek, startTime: '09:00', endTime: '17:00', active: true }));
}
export function validateRecurringDraft(rules: RecurringDraft[]) {
    if (rules.length > 28) return 'Use at most 28 time periods per week.';
    if (rules.some(rule => !rule.startTime || !rule.endTime || rule.endTime <= rule.startTime)) return 'Every time period must end after it starts.';
    for (const day of weekDays) {
        const active = rules.filter(rule => rule.dayOfWeek === day && rule.active).sort((a, b) => a.startTime.localeCompare(b.startTime));
        if (active.some((rule, index) => index > 0 && rule.startTime < active[index - 1].endTime)) return `Time periods overlap on ${day.toLowerCase()}.`;
    }
    return '';
}
