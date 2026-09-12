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

    private final Map<String, HikariDataSource> dataSources =
        new  ConcurrentHashMap<>();

    public TenantDataSourceManager(TenantRepository tenantRepository,
                                   @Value("${app.tenant-database.host:localhost}") String jdbcHost,
                                   @Value("${app.tenant-database.username:booking}") String username,
                                   @Value("${app.tenant-database.password:booking}") String password) {

        this.tenantRepository = tenantRepository;
        this.jdbcHost = jdbcHost;
        this.username = username;
        this.password = password;}

    public DataSource getDataSource(String tenantId){
        return dataSources.computeIfAbsent(tenantId, this::createDataSource);
    }

    private HikariDataSource createDataSource(String tenantId) {
        
        Tenant tenant = tenantRepository.findBySlug(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
    
        if(!"ACTIVE".equals(tenant.getStatus())) {
        throw new IllegalStateException("Tenant is not active: " + tenantId);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + jdbcHost + ":" + "5432" + "/" + tenant.getDatabaseName()); 
        
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

    public void closeAll() {
        dataSources.values().forEach(HikariDataSource::close);
        dataSources.clear();
    }
}

