package com.example.booking.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class JwtValidationTest {

    private final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(
        java.nio.charset.StandardCharsets.UTF_8
    );

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(
            new SecretKeySpec(key, "HmacSHA256")
        )
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

        decoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(
                    "https://identity.example/realms/booking"
                ),
                JwtSecurityConfig.audienceValidator("booking-backend")
            )
        );

        return decoder;
    }

    private String token(
        String audience,
        String issuer,
        Instant expiry,
        byte[] signingKey
    ) throws Exception {
        SignedJWT token = new SignedJWT(
            new JWSHeader(JWSAlgorithm.HS256),
            new JWTClaimsSet.Builder()
                .subject("test-subject")
                .issuer(issuer)
                .audience(audience)
                .expirationTime(Date.from(expiry))
                .notBeforeTime(Date.from(Instant.now().minusSeconds(60)))
                .build()
        );

        token.sign(new MACSigner(signingKey));

        return token.serialize();
    }

    @Test
    void acceptsOnlyCorrectlySignedUnexpiredTokensForThisIssuerAndAudience()
        throws Exception {
        String issuer = "https://identity.example/realms/booking";

        Instant future = Instant.now().plusSeconds(300);

        assertEquals(
            "test-subject",
            decoder()
                .decode(token("booking-backend", issuer, future, key))
                .getSubject()
        );

        assertThrows(JwtException.class, () ->
            decoder().decode(token("other-app", issuer, future, key))
        );

        assertThrows(JwtException.class, () ->
            decoder().decode(
                token(
                    "booking-backend",
                    "https://attacker.example",
                    future,
                    key
                )
            )
        );

        assertThrows(JwtException.class, () ->
            decoder().decode(
                token(
                    "booking-backend",
                    issuer,
                    Instant.now().minusSeconds(300),
                    key
                )
            )
        );

        assertThrows(JwtException.class, () ->
            decoder().decode(
                token(
                    "booking-backend",
                    issuer,
                    future,
                    "abcdef0123456789abcdef0123456789ab".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                    )
                )
            )
        );
    }
}
