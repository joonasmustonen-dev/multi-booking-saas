package com.example.booking.tenant;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class TenantMigrationService {

    private final TenantDataSourceManager dataSourceManager;

    @org.springframework.beans.factory.annotation.Value(
        "${app.tenant-database.migration-username:}"
    )
    private String migrationUsername;

    @org.springframework.beans.factory.annotation.Value(
        "${app.tenant-database.migration-password:}"
    )
    private String migrationPassword;

    public TenantMigrationService(TenantDataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
    }

    public void migrate(String tenantSlug) {
        DataSource dataSource = dataSourceManager.getDataSource(tenantSlug);

        var configuration = Flyway.configure();

        if (migrationUsername != null && !migrationUsername.isBlank()) {
            configuration.dataSource(
                ((com.zaxxer.hikari.HikariDataSource) dataSource).getJdbcUrl(),
                migrationUsername,
                migrationPassword
            );
        } else {
            configuration.dataSource(dataSource);
        }
        configuration
            .locations("classpath:db/tenant")

            // Existing development tenant DBs already contain
            // routing_test_data but were not previously managed by Flyway.
            .baselineOnMigrate(true)
            .baselineVersion(MigrationVersion.fromVersion("0"))

            .load()
            .migrate();
    }
}
