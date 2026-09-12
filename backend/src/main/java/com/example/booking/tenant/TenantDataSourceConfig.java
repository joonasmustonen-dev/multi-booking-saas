package com.example.booking.tenant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;


@Configuration
public class TenantDataSourceConfig {
    
    @Bean
    public TenantRoutingDataSource tenantRoutingDataSource(
            TenantDataSourceManager dataSourceManager,
            DataSource platformDataSource) {
                
        return new TenantRoutingDataSource(
            dataSourceManager,
            platformDataSource
        );
    }
}
