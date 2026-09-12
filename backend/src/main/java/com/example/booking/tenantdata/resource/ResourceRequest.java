package com.example.booking.tenantdata.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResourceRequest(

        @NotBlank
        @Size(max = 200)
        String name,

        @NotNull
        ResourceType type,

        boolean active
) {
}