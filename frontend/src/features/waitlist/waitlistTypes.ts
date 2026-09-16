export type WaitlistStatus =
    | "WAITING"
    | "OFFERED"
    | "ACCEPTED"
    | "EXPIRED"
    | "REMOVED";

export interface WaitlistOffer {
    id: string;
    staffId: string | null;
    staffName: string | null;
    locationId: string | null;
    locationName: string | null;
    resourceId: string | null;
    resourceName: string | null;
    startAt: string;
    endAt: string;
    status: WaitlistStatus;
    notificationChannel: "EMAIL" | "SMS" | "IN_APP";
    notificationQueuedAt: string;
    expiresAt: string;
    appointmentId: string | null;
}

export interface WaitlistEntry {
    id: string;
    customerId: string;
    customerName: string;
    serviceId: string;
    serviceName: string;
    preferredStaffId: string | null;
    preferredStaffName: string | null;
    preferredLocationId: string | null;
    preferredLocationName: string | null;
    windowStart: string;
    windowEnd: string;
    expiresAt: string;
    status: WaitlistStatus;
    notificationConsent: boolean;
    consentRecordedAt: string | null;
    createdAt: string;
    offer: WaitlistOffer | null;
}

export interface CreateWaitlistEntry {
    customerId: string;
    serviceId: string;
    preferredStaffId: string | null;
    preferredLocationId: string | null;
    windowStart: string;
    windowEnd: string;
    expiresAt: string | null;
    notificationConsent: boolean;
}
