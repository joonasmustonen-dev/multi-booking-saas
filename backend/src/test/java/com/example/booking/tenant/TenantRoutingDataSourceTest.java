package com.example.booking.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TenantRoutingDataSourceTest {

    private final TenantContextFilter filter = new TenantContextFilter();
    private final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    private final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void tenantAResolvesToTenantA() {
        TenantContext.setTenantId("tenant-a");

        TestRoutingDataSource routingDataSource = new TestRoutingDataSource();

        assertEquals("tenant-a", routingDataSource.lookupKey());
    }

    @Test
    void noTenantResolvesToNull() {
        TenantContext.clear();

        TestRoutingDataSource routingDataSource = new TestRoutingDataSource();

        assertNull(routingDataSource.lookupKey());
    }

    private static class TestRoutingDataSource extends TenantRoutingDataSource {
        TestRoutingDataSource() {
            super(null, Mockito.mock(DataSource.class));
        }

        Object lookupKey() {
            return determineCurrentLookupKey();
        }
    }

    @Test 
    void tenantFilterClearsContextAfterRequest() throws Exception{

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tenant_id", "tenant-a")
                .build();
        
        Authentication authentication = new JwtAuthenticationToken(jwt);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        filter.doFilter(
                request, 
                response, 
                (req, res) -> {
                    // Inside the filter chain, the tenant context should be set
                    assertEquals("tenant-a", TenantContext.getTenantId());
                }
        );

        assertNull(TenantContext.getTenantId());
    }

    @Test
    void tenantContextMustNotLeakBetweenRequests(){
        TenantContext.setTenantId("tenant-a");
        assertEquals("tenant-a", TenantContext.getTenantId());

        // Simulate request completion

        TenantContext.clear();

        // Simulate the same worker thread being reused for a new request

        assertNull(TenantContext.getTenantId());
    }
}