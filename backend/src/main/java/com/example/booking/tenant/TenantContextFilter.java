package com.example.booking.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_CLAIM = "tenant_id";
    private static final String WORKSPACE_HEADER = "X-Workspace";

    private TenantAccessService tenantAccess;

    private WorkspaceAccessService workspaceAccess;

    @org.springframework.beans.factory.annotation.Value(
        "${app.security.membership-enforcement:false}"
    )
    private boolean membershipEnforcement;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTenantAccess(TenantAccessService tenantAccess) {
        this.tenantAccess = tenantAccess;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setWorkspaceAccess(WorkspaceAccessService workspaceAccess) {
        this.workspaceAccess = workspaceAccess;
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
            ) ||
            request
                .getRequestURI()
                .startsWith(request.getContextPath() + "/api/account/") ||
            request
                .getRequestURI()
                .startsWith(request.getContextPath() + "/api/invitations/")
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

            String requestedWorkspace = request.getHeader(WORKSPACE_HEADER);
            Object claim = membershipEnforcement &&
                    requestedWorkspace != null &&
                    !requestedWorkspace.isBlank()
                ? requestedWorkspace
                : jwtAuthentication.getToken().getClaim(TENANT_CLAIM);

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

            if (membershipEnforcement) {
                String subject = jwtAuthentication.getToken().getSubject();
                if (subject == null || subject.isBlank()) {
                    response.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Authenticated account identity is required"
                    );
                    return;
                }
                var membership = workspaceAccess
                    .activeMembership(tenantId, subject)
                    .orElse(null);
                if (membership == null) {
                    response.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Workspace membership is required"
                    );
                    return;
                }
                var authorities = new java.util.ArrayList<>(
                    jwtAuthentication.getAuthorities()
                        .stream()
                        .filter(authority ->
                            authority.getAuthority().equals("ROLE_PLATFORM_ADMIN")
                        )
                        .toList()
                );
                authorities.add(
                    new SimpleGrantedAuthority(
                        "ROLE_" + membership.getRole().name()
                    )
                );
                SecurityContextHolder.getContext().setAuthentication(
                    new JwtAuthenticationToken(
                        jwtAuthentication.getToken(),
                        authorities,
                        jwtAuthentication.getName()
                    )
                );
            }

            TenantContext.setTenantId(tenantId);

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
