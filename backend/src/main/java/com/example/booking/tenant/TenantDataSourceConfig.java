package com.example.booking.tenant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;


@Configuration
public class TenantDataSourceConfig {
    
    @Bean
    public TenantRoutingDataSource tenantRoutingDataSource(
            TenantDataSourceManager dataSourceManager,
            @Qualifier("platformDataSource")
            DataSource platformDataSource) {
                
        return new TenantRoutingDataSource(
            dataSourceManager,
            platformDataSource
        );
    }
}
