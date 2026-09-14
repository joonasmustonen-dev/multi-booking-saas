package com.example.booking.security;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecurityGuard {

    private final Environment environment;

    public ProductionSecurityGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        require(
            environment.getProperty(
                "app.security.jwt-enabled",
                Boolean.class,
                true
            ),
            "Production requires JWT authentication"
        );

        require(
            value(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri"
            ).startsWith("https://"),
            "Production requires an HTTPS identity provider"
        );

        for (String origin : value("app.security.cors-origins").split(",")) {
            require(
                origin.trim().startsWith("https://") && !origin.contains("*"),
                "Production requires explicit HTTPS frontend origins"
            );
        }
        require(
            value("app.tenant-database.ssl-mode").equals("verify-full"),
            "Production tenant databases require verified TLS"
        );

        require(
            value("spring.datasource.url").contains("sslmode=verify-full"),
            "Production platform database requires verified TLS"
        );

        require(
            !value("spring.datasource.username").equals(
                value("spring.flyway.user")
            ),
            "Use separate production runtime and migration database users"
        );
    }

    private String value(String key) {
        String value = environment.getRequiredProperty(key);

        require(!value.isBlank(), "Missing production setting: " + key);

        return value;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
