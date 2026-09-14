package com.example.booking.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class ApiPermissions {

    private ApiPermissions() {}

    public static boolean isTenantAdmin() {
        var authentication =
            SecurityContextHolder.getContext().getAuthentication();

        return (
            authentication != null &&
            authentication
                .getAuthorities()
                .stream()
                .anyMatch(authority ->
                    authority.getAuthority().equals("ROLE_TENANT_ADMIN")
                )
        );
    }
}
