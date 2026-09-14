package com.example.booking.tenant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RestController
@RequestMapping("/api/platform/tenants")
@org.springframework.security.access.prepost.PreAuthorize(
    "hasRole('PLATFORM_ADMIN')"
)
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public record TenantSummary(
        java.util.UUID id,
        String slug,
        String name,
        String status
    ) {}

    @GetMapping
    public List<TenantSummary> getTenants() {
        return tenantService
            .findAll()
            .stream()
            .map(tenant ->
                new TenantSummary(
                    tenant.getId(),
                    tenant.getSlug(),
                    tenant.getName(),
                    tenant.getStatus()
                )
            )
            .toList();
    }
}
