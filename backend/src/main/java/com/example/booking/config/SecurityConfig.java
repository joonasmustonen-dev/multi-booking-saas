package com.example.booking.config;

import com.example.booking.tenant.TenantContextFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    
    @Value("${app.security.jwt-enabled:false}")
    private boolean jwtEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TenantContextFilter tenantContextFilter)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .anyRequest().authenticated()
            );

        if (jwtEnabled) {
            http
                .oauth2ResourceServer(oauth2 ->
                    oauth2.jwt(Customizer.withDefaults())
                )
                .addFilterAfter(
                    tenantContextFilter,
                    BearerTokenAuthenticationFilter.class
                );
        }

        return http.build();
    }

}
