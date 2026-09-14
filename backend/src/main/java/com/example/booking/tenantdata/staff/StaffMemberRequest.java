package com.example.booking.tenantdata.staff;

import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;
public record StaffMemberRequest(
    @NotBlank @Size(max = 150) String name,
    @Email @Size(max = 254) String email,
    @Size(max = 40) String phone,
    Boolean active,
    Boolean freeAgent,
    @Size(max = 100) Set<@NotNull UUID> locationIds
) {}
