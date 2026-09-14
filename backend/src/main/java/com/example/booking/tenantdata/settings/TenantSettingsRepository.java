package com.example.booking.tenantdata.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository
        extends JpaRepository<TenantSettings, Integer> {
}