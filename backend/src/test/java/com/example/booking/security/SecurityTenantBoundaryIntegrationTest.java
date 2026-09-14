package com.example.booking.security;

import com.example.booking.tenant.TenantContext;
import com.example.booking.tenantdata.Customer;
import com.example.booking.tenantdata.CustomerRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Use the same AutoConfigureMockMvc import that your existing
// TenantJwtIntegrationTest already uses.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.security.jwt-enabled=true")
@AutoConfigureMockMvc
@Import(SecurityTenantBoundaryIntegrationTest.JwtTestConfig.class)
class SecurityTenantBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    private UUID aliceCustomerId;

    private UUID bobCustomerId;

    private String aliceEmail;

    private String bobEmail;

    @BeforeEach
    void setup() {
        aliceEmail = "security-alice-" + UUID.randomUUID() + "@test.local";

        bobEmail = "security-bob-" + UUID.randomUUID() + "@test.local";

        TenantContext.setTenantId("tenant-a");

        Customer alice = customerRepository.saveAndFlush(
            new Customer("Security", "Alice", aliceEmail, null)
        );

        aliceCustomerId = alice.getId();

        TenantContext.setTenantId("tenant-b");

        Customer bob = customerRepository.saveAndFlush(
            new Customer("Security", "Bob", bobEmail, null)
        );

        bobCustomerId = bob.getId();

        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        if (aliceCustomerId != null) {
            TenantContext.setTenantId("tenant-a");

            customerRepository
                .findById(aliceCustomerId)
                .ifPresent(customerRepository::delete);
        }

        if (bobCustomerId != null) {
            TenantContext.setTenantId("tenant-b");

            customerRepository
                .findById(bobCustomerId)
                .ifPresent(customerRepository::delete);
        }

        TenantContext.clear();
    }

    @Test
    void aliceOnlySeesTenantA() throws Exception {
        String response = mockMvc
            .perform(
                get("/api/v1/customers").header(
                    "Authorization",
                    "Bearer alice-token"
                )
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertTrue(response.contains(aliceEmail));

        assertFalse(
            response.contains(bobEmail),
            "tenant-a must not see tenant-b customer data"
        );
    }

    @Test
    void bobOnlySeesTenantB() throws Exception {
        String response = mockMvc
            .perform(
                get("/api/v1/customers").header(
                    "Authorization",
                    "Bearer bob-token"
                )
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertTrue(response.contains(bobEmail));

        assertFalse(
            response.contains(aliceEmail),
            "tenant-b must not see tenant-a customer data"
        );
    }

    @Test
    void missingJwtIsRejected() throws Exception {
        mockMvc
            .perform(get("/api/v1/customers"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void customerRoleCannotAccessTenantCustomerAdministration()
        throws Exception {
        mockMvc
            .perform(
                get("/api/v1/customers").header(
                    "Authorization",
                    "Bearer customer-token"
                )
            )
            .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class JwtTestConfig {

        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return token ->
                switch (token) {
                    case "alice-token" -> createJwt(
                        token,
                        "alice",
                        "tenant-a",
                        "TENANT_ADMIN"
                    );
                    case "bob-token" -> createJwt(
                        token,
                        "bob",
                        "tenant-b",
                        "STAFF"
                    );
                    case "customer-token" -> createJwt(
                        token,
                        "customer-user",
                        "tenant-a",
                        "CUSTOMER"
                    );
                    default -> throw new JwtException("Unknown test token");
                };
        }

        private Jwt createJwt(
            String token,
            String username,
            String tenantId,
            String role
        ) {
            Instant now = Instant.now();

            return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("preferred_username", username)
                .claim("tenant_id", tenantId)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .build();
        }
    }
}
