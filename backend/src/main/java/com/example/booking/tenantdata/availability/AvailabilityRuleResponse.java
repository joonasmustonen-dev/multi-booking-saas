package com.example.booking.tenantdata.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityRuleResponse(
        UUID id,
        UUID staffId,
        UUID locationId,
        UUID resourceId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        boolean active
) {

    public static AvailabilityRuleResponse from(
            AvailabilityRule rule) {

        return new AvailabilityRuleResponse(
                rule.getId(),
                rule.getStaff() != null ? rule.getStaff().getId() : null,
                rule.getLocation() != null ? rule.getLocation().getId() : null,
                rule.getResource() != null ? rule.getResource().getId() : null,
                rule.getDayOfWeek(),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.isActive()
        );
    }
}