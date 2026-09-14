package com.example.booking.tenantdata.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record CreateServiceRequest(
    @NotBlank String name,

    String description,

    @NotNull @Min(1) Integer durationMinutes,

    BigDecimal price,

    String currency,

    Set<UUID> staffIds,

    Set<UUID> locationIds,

    Set<UUID> resourceIds,
    @NotNull AssignmentRequirement staffRequirement,
    @NotNull AssignmentRequirement locationRequirement,
    @NotNull AssignmentRequirement resourceRequirement
) {}
