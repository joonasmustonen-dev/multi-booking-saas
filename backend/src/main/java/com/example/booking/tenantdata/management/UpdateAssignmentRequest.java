package com.example.booking.tenantdata.management;

import jakarta.validation.constraints.*;
public record UpdateAssignmentRequest(
    @NotBlank @Size(max = 150) String name,
    @NotNull Boolean active
) {}
