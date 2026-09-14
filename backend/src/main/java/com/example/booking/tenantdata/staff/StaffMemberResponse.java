package com.example.booking.tenantdata.staff;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;
public record StaffMemberResponse(UUID id, String name, boolean active, OffsetDateTime createdAt,
        String email, String phone, boolean freeAgent, Set<UUID> locationIds) {
    public static StaffMemberResponse from(StaffMember entity) {
        return new StaffMemberResponse(entity.getId(), entity.getName(), entity.isActive(), entity.getCreatedAt(),
                entity.getEmail(), entity.getPhone(), entity.isFreeAgent(),
                entity.getLocations().stream().map(location -> location.getId()).collect(Collectors.toSet()));
    }
}
