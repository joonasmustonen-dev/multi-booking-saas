package com.example.booking.security;

import com.example.booking.tenant.TenantContext;
import com.example.booking.tenantdata.privacy.SecurityAuditService;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import java.util.UUID;

@Configuration
public class SecurityWebConfig implements WebMvcConfigurer {

    private final SecurityAuditService audit;

    private final ApiSafetyFilter limits;

    public SecurityWebConfig(
        SecurityAuditService audit,
        ApiSafetyFilter limits
    ) {
        this.audit = audit;

        this.limits = limits;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry
            .addInterceptor(
                new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Object handler
                    ) throws Exception {
                        var authentication =
                            SecurityContextHolder.getContext().getAuthentication();

                        if (
                            authentication != null &&
                            !limits.allow(
                                "user:" +
                                    authentication.getName() +
                                    ":" +
                                    TenantContext.getTenantId(),
                                System.currentTimeMillis()
                            )
                        ) {
                            response.setHeader("Retry-After", "60");

                            response.sendError(
                                429,
                                "Too many requests; try again shortly"
                            );

                            return false;
                        }
                        return true;
                    }

                    @Override
                    public void afterCompletion(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Object handler,
                        Exception exception
                    ) {
                        if (TenantContext.getTenantId() == null) return;
                        boolean export = request
                            .getRequestURI()
                            .endsWith("/export");

                        if (
                            request.getMethod().equals("GET") && !export
                        ) return;
                        if (
                            !java.util.Set.of(
                                "POST",
                                "PUT",
                                "PATCH",
                                "DELETE",
                                "GET"
                            ).contains(request.getMethod())
                        ) return;
                        var authentication =
                            SecurityContextHolder.getContext().getAuthentication();

                        if (authentication == null) return;

                        String actor =
                            authentication instanceof JwtAuthenticationToken jwt
                                ? jwt.getToken().getSubject()
                                : authentication.getName();

                        String[] parts = request
                            .getRequestURI()
                            .substring(request.getContextPath().length())
                            .split("/");

                        if (
                            parts.length < 4 ||
                            !java.util.Set.of(
                                "customers",
                                "staff",
                                "locations",
                                "resources",
                                "services",
                                "appointments",
                                "privacy",
                                "settings"
                            ).contains(parts[3])
                        ) return;
                        UUID record = null;

                        for (String part : parts) {
                            try {
                                record = UUID.fromString(part);
                            } catch (IllegalArgumentException ignored) {}
                        }
                        String action = request.getMethod() + ":" + parts[3];

                        if (
                            parts.length > 4 &&
                            java.util.Set.of(
                                "export",
                                "erase",
                                "privacy",
                                "cancel",
                                "reschedule",
                                "status",
                                "policy",
                                "retention",
                                "apply"
                            ).contains(parts[parts.length - 1])
                        ) {
                            action += ":" + parts[parts.length - 1];
                        }
                        try {
                            audit.record(
                                actor == null ? "unknown" : actor,
                                action,
                                record,
                                (String) request.getAttribute("correlationId"),
                                response.getStatus()
                            );
                        } catch (RuntimeException failure) {
                            org.slf4j.LoggerFactory.getLogger(
                                SecurityWebConfig.class
                            ).error(
                                "Audit persistence failed for request {}",
                                request.getAttribute("correlationId")
                            );
                        }
                    }
                }
            )
            .addPathPatterns("/api/v1/**");
    }
}
