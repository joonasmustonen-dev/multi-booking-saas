package com.example.booking.tenantdata.availability;

public enum AvailabilityOwnerType {
    STAFF,
    LOCATION,
    RESOURCE;

    public static AvailabilityOwnerType fromPath(String path) {
        return switch (path) {
            case "staff" -> STAFF;
            case "locations" -> LOCATION;
            case "resources" -> RESOURCE;
            default -> throw new IllegalArgumentException(
                "Unknown availability owner"
            );
        };
    }
}
