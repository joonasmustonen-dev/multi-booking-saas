package com.example.booking.tenantdata.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record CreateServiceRequest(
    @NotBlank @jakarta.validation.constraints.Size(max = 200) String name,

    @jakarta.validation.constraints.Size(max = 2000) String description,

    @NotNull
    @Min(1)
    @jakarta.validation.constraints.Max(1440)
    Integer durationMinutes,

    @jakarta.validation.constraints.PositiveOrZero
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 2)
    BigDecimal price,

    @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{3}")
    String currency,

    @jakarta.validation.constraints.Size(max = 100) Set<@NotNull UUID> staffIds,

    @jakarta.validation.constraints.Size(max = 100)
    Set<@NotNull UUID> locationIds,

    @jakarta.validation.constraints.Size(max = 100)
    Set<@NotNull UUID> resourceIds,
    @NotNull AssignmentRequirement staffRequirement,
    @NotNull AssignmentRequirement locationRequirement,
    @NotNull AssignmentRequirement resourceRequirement
) {}
