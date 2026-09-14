package com.example.booking.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@ConditionalOnProperty(
    name = "app.security.jwt-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class JwtSecurityConfig {

    public static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt ->
            jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                      new OAuth2Error(
                          "invalid_token",
                          "Invalid token audience",
                          null
                      )
                  );
    }

    @Bean
    JwtDecoder bookingJwtDecoder(
        @Value(
            "${spring.security.oauth2.resourceserver.jwt.issuer-uri}"
        ) String issuer,
        @Value("${app.security.audience:booking-backend}") String audience
    ) {
        if (audience.isBlank()) {
            throw new IllegalStateException(
                "A backend JWT audience is required"
            );
        }

        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(
                issuer
            ).build();

            decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuer),
                    audienceValidator(audience)
                )
            );

            return decoder;
        });
    }
}
