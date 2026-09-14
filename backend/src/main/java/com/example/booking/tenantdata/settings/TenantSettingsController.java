package com.example.booking.tenantdata.settings;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
public class TenantSettingsController {

    private final TenantSettingsService service;

    public TenantSettingsController(TenantSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'CUSTOMER')")
    public TenantSettingsResponse getSettings() {
        return service.getSettings();
    }

    @PutMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public TenantSettingsResponse update(
        @Valid @RequestBody UpdateTenantSettingsRequest request
    ) {
        return service.update(request);
    }
}
