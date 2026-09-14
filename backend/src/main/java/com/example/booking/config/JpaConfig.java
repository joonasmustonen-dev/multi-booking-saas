package com.example.booking.config;

import com.example.booking.tenant.TenantRepository;
import com.example.booking.tenant.TenantRoutingDataSource;

import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

@Configuration
public class JpaConfig {

    @Bean(name = "platformEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean platformEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        DataSource platformDataSource
    ) {
        return builder
            .dataSource(platformDataSource)
            .packages("com.example.booking.tenant")
            .persistenceUnit("platform")
            .build();
    }

    @Bean(name = "platformTransactionManager")
    @Primary
    public JpaTransactionManager platformTransactionManager(
        EntityManagerFactory platformEntityManagerFactory
    ) {
        return new JpaTransactionManager(platformEntityManagerFactory);
    }

    @Bean(name = "tenantEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        TenantRoutingDataSource tenantRoutingDataSource
    ) {
        return builder
            .dataSource(tenantRoutingDataSource)
            .packages("com.example.booking.tenantdata")
            .persistenceUnit("tenant")
            .build();
    }

    @Bean(name = "tenantTransactionManager")
    public JpaTransactionManager tenantTransactionManager(
        @org.springframework.beans.factory.annotation.Qualifier(
            "tenantEntityManagerFactory"
        ) EntityManagerFactory tenantEntityManagerFactory
    ) {
        return new JpaTransactionManager(tenantEntityManagerFactory);
    }
}
