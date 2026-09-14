package com.example.booking.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantContextFilter extends OncePerRequestFilter {
    
    private static final String TENANT_CLAIM = "tenant_id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return (request.getContextPath() + "/api/health")
                .equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) 
            throws ServletException, IOException {
        
            try {
            Authentication authentication =
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication();

            if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "JWT authentication required"
                );
                return;
            }

            String tenantId =
                    jwtAuthentication
                            .getToken()
                            .getClaimAsString("tenant_id");

            if (tenantId == null || tenantId.isBlank()) {
                response.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "tenant_id claim is required"
                );
                return;
            }

            TenantContext.setTenantId(tenantId);

            filterChain.doFilter(request, response);

        } finally {
            TenantContext.clear();
        }
    }
    
}
