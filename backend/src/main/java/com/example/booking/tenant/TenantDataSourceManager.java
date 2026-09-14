package com.example.booking.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TenantDataSourceManager {

    private final TenantRepository tenantRepository;

    private final String jdbcHost;

    private final String username;

    private final String password;

    @Value("${app.tenant-database.port:5432}")
    private int jdbcPort = 5432;

    @Value("${app.tenant-database.ssl-mode:}")
    private String sslMode = "";

    private final Map<String, HikariDataSource> dataSources =
        new ConcurrentHashMap<>();

    public TenantDataSourceManager(
        TenantRepository tenantRepository,
        @Value("${app.tenant-database.host:localhost}") String jdbcHost,
        @Value("${app.tenant-database.username:booking}") String username,
        @Value("${app.tenant-database.password:booking}") String password
    ) {
        this.tenantRepository = tenantRepository;

        this.jdbcHost = jdbcHost;

        this.username = username;

        this.password = password;
    }

    public DataSource getDataSource(String tenantId) {
        Tenant current = tenantRepository
            .findBySlug(tenantId)
            .filter(tenant -> "ACTIVE".equals(tenant.getStatus()))
            .orElse(null);

        if (current == null) {
            closeDataSource(tenantId);

            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Workspace is unavailable"
            );
        }

        if (!current.getDatabaseName().matches("[A-Za-z0-9_]{1,63}")) {
            throw new IllegalStateException(
                "Invalid workspace database identifier"
            );
        }
        HikariDataSource cached = dataSources.get(tenantId);

        if (
            cached != null && !cached.getJdbcUrl().equals(jdbcUrl(current))
        ) closeDataSource(tenantId);

        return dataSources.computeIfAbsent(tenantId, this::createDataSource);
    }

    private HikariDataSource createDataSource(String tenantId) {
        Tenant tenant = tenantRepository
            .findBySlug(tenantId)
            .orElseThrow(() ->
                new IllegalArgumentException("Unknown tenant: " + tenantId)
            );

        if (!"ACTIVE".equals(tenant.getStatus())) {
            throw new IllegalStateException(
                "Tenant is not active: " + tenantId
            );
        }

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(jdbcUrl(tenant));

        config.setUsername(username);

        config.setPassword(password);

        /*
         * Make the tenant identity part of the pool name.
         * This is useful when diagnosing accidental pool reuse.
         */

        config.setPoolName("tenant-" + tenantId);

        config.setMaximumPoolSize(5);

        config.setMinimumIdle(1);

        return new HikariDataSource(config);
    }

    public void closeDataSource(String tenantId) {
        HikariDataSource dataSource = dataSources.remove(tenantId);

        if (dataSource != null) {
            dataSource.close();
        }
    }

    private String jdbcUrl(Tenant tenant) {
        if (
            !sslMode.isBlank() &&
            !java.util.Set.of(
                "disable",
                "allow",
                "prefer",
                "require",
                "verify-ca",
                "verify-full"
            ).contains(sslMode)
        ) {
            throw new IllegalStateException("Invalid database SSL mode");
        }
        return (
            "jdbc:postgresql://" +
            jdbcHost +
            ":" +
            jdbcPort +
            "/" +
            tenant.getDatabaseName() +
            (sslMode.isBlank() ? "" : "?sslmode=" + sslMode)
        );
    }

    @jakarta.annotation.PreDestroy
    public void closeAll() {
        dataSources.values().forEach(HikariDataSource::close);

        dataSources.clear();
    }
}
