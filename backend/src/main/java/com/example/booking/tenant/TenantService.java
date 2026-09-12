package com.example.booking.tenant;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TenantService {
    
    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }
}
