package com.example.booking.tenantdata.appointment;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    public boolean canTransitionTo(AppointmentStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == COMPLETED ||
                target == NO_SHOW ||
                target == CANCELLED;
            case CANCELLED, COMPLETED, NO_SHOW -> false;
        };
    }
}
