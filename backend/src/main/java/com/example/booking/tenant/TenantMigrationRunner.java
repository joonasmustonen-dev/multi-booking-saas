package com.example.booking.tenant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TenantMigrationRunner implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final TenantMigrationService migrationService;

    public TenantMigrationRunner(
            TenantRepository tenantRepository,
            TenantMigrationService migrationService) {

        this.tenantRepository = tenantRepository;
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {

        tenantRepository.findByStatus("ACTIVE")
                .forEach(tenant ->
                        migrationService.migrate(
                                tenant.getSlug()
                        )
                );
    }
}
