package com.example.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.example.booking.tenant",
        entityManagerFactoryRef = "platformEntityManagerFactory",
        transactionManagerRef = "platformTransactionManager"
)

public class PlatformJpaRepositoryConfig {
    
}
