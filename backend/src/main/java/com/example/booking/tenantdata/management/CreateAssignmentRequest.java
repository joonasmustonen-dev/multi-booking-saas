package com.example.booking.tenantdata.management;

import jakarta.validation.constraints.*;
public record CreateAssignmentRequest(@NotBlank @Size(max = 150) String name) {}
