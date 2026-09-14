package com.example.booking.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    properties = "app.security.jwt-enabled=true"
)
@AutoConfigureMockMvc 
public class TenantJwtIntegrationTest {

    @Autowired 
    private MockMvc mockMvc;

    @Test
    void healthDoesNotRequireJwtOrTenantClaim() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @AfterEach 
    void cleanup(){
        TenantContext.clear();
    }
    
@TestConfiguration
static class TestEndpointConfiguration {

    @Bean
    TestTenantController testTenantController(
            TenantRoutingDataSource routingDataSource) {

        return new TestTenantController(routingDataSource);
    }
}

    @RestController
    static class TestTenantController {

        private final TenantRoutingDataSource routingDataSource;

        TestTenantController(
                TenantRoutingDataSource routingDataSource) {

            this.routingDataSource = routingDataSource;
        }

        @GetMapping("/api/test/tenant-context")
        String tenantContext() {

            String tenantId =
                    TenantContext.getTenantId();

            return tenantId == null
                    ? ""
                    : tenantId;
        }

        @GetMapping("/api/test/tenant-database")
        String tenantDatabase() throws Exception {

            try (var connection =
                        routingDataSource.getConnection()) {

                return connection
                        .getMetaData()
                        .getURL();
            }
        }
    }

    @Test
    void jwtTenantClaimCreatesTenantContextDuringRequest()
            throws Exception {

        mockMvc.perform(
                get("/api/test/tenant-context")
                        .with(jwt().jwt(jwt ->
                                jwt.claim("tenant_id", "tenant-a")
                        ))
        )
        .andExpect(status().isOk())
        .andExpect(result ->
                org.junit.jupiter.api.Assertions.assertEquals(
                        "tenant-a",
                        result.getResponse().getContentAsString()
                )
        );

        assertNull(
                TenantContext.getTenantId(),
                "TenantContext must be cleared after the request"
        );
    }
    @Test
    void jwtTenantClaimResolvesTenantB() throws Exception {

        mockMvc.perform(
                get("/api/test/tenant-context")
                        .with(jwt().jwt(jwt ->
                                jwt.claim(
                                        "tenant_id",
                                        "tenant-b"
                                )
                        ))
            )
            .andExpect(status().isOk())
            .andExpect(
                result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(
                            "tenant-b",
                            result.getResponse().getContentAsString()
                    );
                }
            );

        assertNull(TenantContext.getTenantId());
    }


    @TestConfiguration
    static class JwtTestConfiguration {

        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Real JWT decoding is disabled in tests");
            };
        }
    }


    @Test
    void authenticatedRequestWithoutTenantIsForbidden()
            throws Exception {

        mockMvc.perform(
                get("/api/test/tenant-database")
                        .with(jwt())
            )
            .andExpect(status().isForbidden());

        assertNull(TenantContext.getTenantId());
    }
    @Test
    void jwtTenantAReachesTenantADatabase()
            throws Exception {

        mockMvc.perform(
                get("/api/test/tenant-database")
                        .with(jwt().jwt(jwt ->
                                jwt.claim(
                                        "tenant_id",
                                        "tenant-a"
                                )
                        ))
            )
            .andExpect(status().isOk())
            .andExpect(result ->
                    org.junit.jupiter.api.Assertions.assertEquals(
                            "jdbc:postgresql://localhost:5432/tenant_a",
                            result.getResponse()
                                    .getContentAsString()
                    )
            );

        assertNull(TenantContext.getTenantId());
    }

    
}
