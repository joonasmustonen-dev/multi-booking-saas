export type DayOfWeek =
    | "MONDAY"
    | "TUESDAY"
    | "WEDNESDAY"
    | "THURSDAY"
    | "FRIDAY"
    | "SATURDAY"
    | "SUNDAY";

export interface AvailabilityRule {
    id: string;
    staffId: string | null;
    locationId: string | null;
    resourceId: string | null;
    dayOfWeek: DayOfWeek;
    startTime: string;
    endTime: string;
    active: boolean;
}

export interface CreateAvailabilityRuleRequest {
    dayOfWeek: DayOfWeek;
    startTime: string;
    endTime: string;
}

export interface AvailabilityException {
    id: string;
    staffId: string | null;
    locationId: string | null;
    resourceId: string | null;
    startAt: string;
    endAt: string;
    available: boolean;
}

export interface CreateAvailabilityExceptionRequest {
    startAt: string;
    endAt: string;
    available: boolean;
}