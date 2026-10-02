package com.samyak.job_intelligence.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("15m") Duration accessTokenExpiration,
        @DefaultValue("7d") Duration refreshTokenExpiration,
        @DefaultValue("job-intelligence") String issuer
) {

    /** HS256 requires a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank() || secret.contains("${")) {
            throw new IllegalStateException(
                    "security.jwt.secret is not configured. Set the JWT_SECRET environment variable."
            );
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "security.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long."
            );
        }
        if (accessTokenExpiration == null || accessTokenExpiration.isZero() || accessTokenExpiration.isNegative()) {
            throw new IllegalStateException("security.jwt.access-token-expiration must be positive.");
        }
        if (refreshTokenExpiration == null || refreshTokenExpiration.isZero() || refreshTokenExpiration.isNegative()) {
            throw new IllegalStateException("security.jwt.refresh-token-expiration must be positive.");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("security.jwt.issuer must not be blank.");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[accessTokenExpiration=" + accessTokenExpiration
                + ", refreshTokenExpiration=" + refreshTokenExpiration
                + ", issuer=" + issuer + "]";
    }
}
