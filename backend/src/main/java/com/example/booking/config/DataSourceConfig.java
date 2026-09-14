package com.example.booking.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean(name = "platformDataSource")
    @Primary
    public DataSource platformDataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password,
        @Value(
            "${app.platform-database.maximum-pool-size:5}"
        ) int maximumPoolSize
    ) {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(url);

        config.setUsername(username);

        config.setPassword(password);

        config.setPoolName("platform-pool");

        config.setMaximumPoolSize(maximumPoolSize);

        config.setMinimumIdle(1);

        return new HikariDataSource(config);
    }
}
