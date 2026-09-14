package com.example.booking.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantAccessService {

    private final TenantRepository tenants;

    public TenantAccessService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Transactional(value = "platformTransactionManager", readOnly = true)
    public Tenant requireActive(String slug) {
        return tenants
            .findBySlug(slug)
            .filter(tenant -> "ACTIVE".equals(tenant.getStatus()))
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Workspace is unavailable"
                )
            );
    }
}
