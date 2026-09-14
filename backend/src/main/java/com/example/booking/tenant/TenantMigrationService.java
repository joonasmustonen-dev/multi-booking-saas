package com.example.booking.tenant;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class TenantMigrationService {

    private final TenantDataSourceManager dataSourceManager;

    public TenantMigrationService(TenantDataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
    }

    public void migrate(String tenantSlug) {
        DataSource dataSource = dataSourceManager.getDataSource(tenantSlug);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/tenant")

            // Existing development tenant DBs already contain
            // routing_test_data but were not previously managed by Flyway.
            .baselineOnMigrate(true)
            .baselineVersion(MigrationVersion.fromVersion("0"))

            .load()
            .migrate();
    }
}
