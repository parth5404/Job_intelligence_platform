package com.samyak.job_intelligence.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.samyak.job_intelligence.auth.config.JwtProperties;
import com.samyak.job_intelligence.auth.exception.InvalidTokenException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Issues and validates HS256 signed JWTs (Nimbus JOSE via Spring Security).
 *
 * Every token carries: {@code sub} and {@code cid} (candidate id), {@code email},
 * {@code type} ({@code access} or {@code refresh}), {@code jti}, {@code iss}, {@code iat}, {@code exp}.
 */
@Component
public class JwtTokenProvider {

    static final String CLAIM_CANDIDATE_ID = "cid";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_TYPE = "type";

    private final JwtProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;

        SecretKey key = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.issuer())
        );
        nimbusDecoder.setJwtValidator(validator);
        this.decoder = nimbusDecoder;
    }

    public IssuedToken issueAccessToken(Long candidateId, String email) {
        return issue(candidateId, email, TokenType.ACCESS, properties.accessTokenExpiration());
    }

    public IssuedToken issueRefreshToken(Long candidateId, String email) {
        return issue(candidateId, email, TokenType.REFRESH, properties.refreshTokenExpiration());
    }

    private IssuedToken issue(Long candidateId, String email, TokenType type, Duration lifetime) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(lifetime);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(candidateId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_CANDIDATE_ID, candidateId)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, type.claimValue())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Validates signature, expiry, issuer, token type and the id/email claims.
     *
     * @throws InvalidTokenException for any failure; the reason is intentionally not exposed
     */
    public TokenClaims parse(String token, TokenType expectedType) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException();
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException(ex);
        }

        TokenType type = TokenType.fromClaim(jwt.getClaim(CLAIM_TYPE));
        if (type == null || type != expectedType) {
            throw new InvalidTokenException();
        }

        Object cid = jwt.getClaim(CLAIM_CANDIDATE_ID);
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        if (!(cid instanceof Number number)
                || subject == null
                || email == null || email.isBlank()
                || jwt.getId() == null
                || jwt.getExpiresAt() == null) {
            throw new InvalidTokenException();
        }
        long candidateId = number.longValue();
        if (!String.valueOf(candidateId).equals(subject)) {
            throw new InvalidTokenException();
        }

        return new TokenClaims(candidateId, email, type, jwt.getId(), jwt.getExpiresAt());
    }
}
