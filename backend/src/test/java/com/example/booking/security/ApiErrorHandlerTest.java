package com.example.booking.security;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class ApiErrorHandlerTest {

    @Test
    void malformedJsonIsAClientErrorAndSqlDetailsNeverReachTheResponse() {
        ApiErrorHandler handler = new ApiErrorHandler();

        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setAttribute("correlationId", "request-test");

        var malformed = handler.handle(
            new HttpMessageNotReadableException(
                "Sensitive payload",
                new MockHttpInputMessage(new byte[0])
            ),
            request
        );

        assertEquals(400, malformed.getStatusCode().value());

        var conflict = handler.handle(
            new DataIntegrityViolationException(
                "SQL INSERT customer alice@example.test; secret notes"
            ),
            request
        );

        assertEquals(409, conflict.getStatusCode().value());

        assertFalse(conflict.getBody().getDetail().contains("alice"));

        assertFalse(conflict.getBody().getDetail().contains("SQL"));

        assertEquals(
            "request-test",
            conflict.getBody().getProperties().get("requestId")
        );
    }

    @Test
    void productionRejectsAuthenticationAndTransportDowngrades() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.security.jwt-enabled", "true")
            .withProperty(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                "https://identity.example.test/realms/booking"
            )
            .withProperty(
                "app.security.cors-origins",
                "https://booking.example.test"
            )
            .withProperty("app.tenant-database.ssl-mode", "verify-full")
            .withProperty(
                "spring.datasource.url",
                "jdbc:postgresql://db.example.test/platform?sslmode=verify-full"
            )
            .withProperty("spring.datasource.username", "runtime")
            .withProperty("spring.flyway.user", "migrator");

        ProductionSecurityGuard guard = new ProductionSecurityGuard(
            environment
        );

        assertDoesNotThrow(guard::validate);

        environment.setProperty("app.security.jwt-enabled", "false");

        assertThrows(IllegalStateException.class, guard::validate);

        environment.setProperty("app.security.jwt-enabled", "true");

        environment.setProperty(
            "app.security.cors-origins",
            "http://booking.example.test"
        );

        assertThrows(IllegalStateException.class, guard::validate);

        environment.setProperty(
            "app.security.cors-origins",
            "https://booking.example.test"
        );

        environment.setProperty("spring.datasource.username", "migrator");

        assertThrows(IllegalStateException.class, guard::validate);
    }
}
