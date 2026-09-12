package com.example.booking.tenantdata.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record UpdateServiceRequest(

        @NotBlank
        @Size(max = 200)
        String name,

        String description,

        @Min(1)
        int durationMinutes,

        @DecimalMin("0.00")
        BigDecimal price,

        @Size(min = 3, max = 3)
        String currency,

        boolean active,

        Set<UUID> resourceIds
) {
}