package com.example.booking.tenantdata.service;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record CreateServiceRequest(

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

        Set<UUID> resourceIds
) {
}