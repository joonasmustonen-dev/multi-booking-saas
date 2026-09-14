package com.example.booking.config;

import com.example.booking.tenant.TenantContextFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.security.jwt-enabled:true}")
    private boolean jwtEnabled;

    @Value("${app.security.cors-origins:http://localhost:5173}")
    private List<String> corsOrigins;

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantFilterRegistration(
        TenantContextFilter tenantContextFilter
    ) {
        FilterRegistrationBean<TenantContextFilter> registration =
            new FilterRegistrationBean<>(tenantContextFilter);

        // Run only in the security chain, after bearer-token authentication.
        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        TenantContextFilter tenantContextFilter,
        KeycloakJwtAuthenticationConverter jwtAuthenticationConverter,
        CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS
                )
            )
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers("/api/health")
                    .permitAll()
                    .requestMatchers("/api/platform/**")
                    .hasRole("PLATFORM_ADMIN")
                    .anyRequest()
                    .authenticated()
            );

        if (jwtEnabled) {
            http.oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
            ).addFilterAfter(
                tenantContextFilter,
                BearerTokenAuthenticationFilter.class
            );
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(corsOrigins);

        configuration.setAllowedMethods(
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(
            List.of("Authorization", "Content-Type")
        );

        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }
}
