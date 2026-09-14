package com.example.booking.tenant;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void tearDown() {
        TenantContext.clear();

        SecurityContextHolder.clearContext();
    }

    private Jwt createJwt(String tenantId) {
        return Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("sub", "test-user")
            .claim("tenant_id", tenantId)
            .build();
    }

    private Jwt createJwtWithoutTenant() {
        return Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("sub", "test-user")
            .build();
    }

    @Test
    void jwtTenantClaimSetsTenantContext() throws Exception {
        Jwt jwt = createJwt("tenant-a");

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertEquals("tenant-a", TenantContext.getTenantId());
        };

        filter.doFilter(request, response, chain);

        assertNull(
            TenantContext.getTenantId(),
            "TenantContext must be cleared after request"
        );
    }

    @Test
    void differentJwtResolvesDifferentTenant() throws Exception {
        Jwt jwt = createJwt("tenant-b");

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertEquals("tenant-b", TenantContext.getTenantId());
        };

        filter.doFilter(request, response, chain);

        assertNull(TenantContext.getTenantId());
    }

    @Test
    void jwtWithoutTenantDoesNotCreateTenantContext() throws Exception {
        Jwt jwt = createJwtWithoutTenant();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertNull(TenantContext.getTenantId());
        };

        filter.doFilter(request, response, chain);

        assertNull(TenantContext.getTenantId());
    }

    @Test
    void previousTenantCannotLeakIntoNextRequest() throws Exception {
        // Request A

        Jwt jwtA = createJwt("tenant-a");

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtA)
        );

        MockHttpServletRequest requestA = new MockHttpServletRequest();

        MockHttpServletResponse responseA = new MockHttpServletResponse();

        FilterChain chainA = (req, res) -> {
            assertEquals("tenant-a", TenantContext.getTenantId());
        };

        filter.doFilter(requestA, responseA, chainA);

        // The filter should have cleared it

        assertNull(TenantContext.getTenantId());

        // Remove auth so request b has no JWT

        SecurityContextHolder.clearContext();

        // Request B

        MockHttpServletRequest requestB = new MockHttpServletRequest();

        MockHttpServletResponse responseB = new MockHttpServletResponse();

        FilterChain chainB = (req, res) -> {
            assertNull(
                TenantContext.getTenantId(),
                "Tenant A must not leak into request B"
            );
        };

        filter.doFilter(requestB, responseB, chainB);
    }
}
