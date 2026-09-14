package com.example.booking.tenantdata.management;

import jakarta.validation.constraints.NotNull;
public record AssignmentActiveRequest(@NotNull Boolean active) {}
