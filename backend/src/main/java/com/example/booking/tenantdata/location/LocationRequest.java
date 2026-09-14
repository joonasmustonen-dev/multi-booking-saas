package com.example.booking.tenantdata.location;

import jakarta.validation.constraints.*;
public record LocationRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 1000) String description,
    @Size(max = 200) String addressLine,
    @Size(max = 100) String city,
    @Size(max = 30) String postalCode,
    @Size(max = 2) @Pattern(regexp = "[A-Z]{2}|^$") String countryCode,
    @Size(max = 40) String phone,
    Boolean active
) {}
