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

    private TenantAccessService tenantAccess;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTenantAccess(TenantAccessService tenantAccess) {
        this.tenantAccess = tenantAccess;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return (
            (request.getRequestURI() != null &&
                request
                    .getRequestURI()
                    .startsWith(request.getContextPath() + "/api/platform/")) ||
            (request.getContextPath() + "/api/health").equals(
                request.getRequestURI()
            )
        );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

            if (
                !(authentication instanceof
                    JwtAuthenticationToken jwtAuthentication)
            ) {
                response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "JWT authentication required"
                );

                return;
            }

            Object claim = jwtAuthentication.getToken().getClaim(TENANT_CLAIM);

            if (
                !(claim instanceof String tenantId) ||
                !tenantId.matches("[a-z0-9][a-z0-9-]{0,62}")
            ) {
                response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "tenant_id claim is required"
                );

                return;
            }

            try {
                if (tenantAccess != null) tenantAccess.requireActive(tenantId);
            } catch (
                org.springframework.web.server.ResponseStatusException unavailable
            ) {
                response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Workspace is unavailable"
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
