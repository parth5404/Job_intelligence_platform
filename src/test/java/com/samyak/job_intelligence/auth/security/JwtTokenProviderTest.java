package com.samyak.job_intelligence.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.samyak.job_intelligence.auth.config.JwtProperties;
import com.samyak.job_intelligence.auth.exception.InvalidTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-which-is-long-enough-0123456789";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final JwtProperties properties = new JwtProperties(
            SECRET,
            Duration.ofMinutes(15),
            Duration.ofDays(7),
            "job-intelligence"
    );

    private JwtTokenProvider providerAt(Instant instant) {
        return new JwtTokenProvider(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void shouldRoundTripAccessToken() {
        JwtTokenProvider provider = providerAt(NOW);

        IssuedToken token = provider.issueAccessToken(42L, "a@example.com");
        TokenClaims claims = provider.parse(token.value(), TokenType.ACCESS);

        assertThat(claims.candidateId()).isEqualTo(42L);
        assertThat(claims.email()).isEqualTo("a@example.com");
        assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void shouldRoundTripRefreshToken() {
        JwtTokenProvider provider = providerAt(NOW);

        IssuedToken token = provider.issueRefreshToken(42L, "a@example.com");
        TokenClaims claims = provider.parse(token.value(), TokenType.REFRESH);

        assertThat(claims.type()).isEqualTo(TokenType.REFRESH);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void shouldIssueUniqueTokensEvenWithinTheSameSecond() {
        JwtTokenProvider provider = providerAt(NOW);

        IssuedToken first = provider.issueRefreshToken(42L, "a@example.com");
        IssuedToken second = provider.issueRefreshToken(42L, "a@example.com");

        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    void shouldRejectRefreshTokenUsedAsAccessToken() {
        JwtTokenProvider provider = providerAt(NOW);
        IssuedToken refresh = provider.issueRefreshToken(42L, "a@example.com");

        assertThatThrownBy(() -> provider.parse(refresh.value(), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldRejectAccessTokenUsedAsRefreshToken() {
        JwtTokenProvider provider = providerAt(NOW);
        IssuedToken access = provider.issueAccessToken(42L, "a@example.com");

        assertThatThrownBy(() -> provider.parse(access.value(), TokenType.REFRESH))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldRejectTamperedToken() {
        JwtTokenProvider provider = providerAt(NOW);
        String token = provider.issueAccessToken(42L, "a@example.com").value();

        String[] parts = token.split("\\.");
        // Flip the payload to claim a different candidate while keeping the original signature.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"43\",\"cid\":43,\"email\":\"a@example.com\",\"type\":\"access\","
                        + "\"iss\":\"job-intelligence\",\"jti\":\"x\",\"exp\":" + NOW.plusSeconds(600).getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> provider.parse(forged, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldRejectTokenSignedWithDifferentSecret() {
        JwtProperties otherProperties = new JwtProperties(
                "a-completely-different-secret-0123456789-abcdef",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                "job-intelligence"
        );
        JwtTokenProvider other = new JwtTokenProvider(otherProperties, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = other.issueAccessToken(42L, "a@example.com").value();

        assertThatThrownBy(() -> providerAt(NOW).parse(token, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = providerAt(NOW).issueAccessToken(42L, "a@example.com").value();

        // Still valid just before expiry, invalid at and after expiry.
        JwtTokenProvider justBeforeExpiry = providerAt(NOW.plus(Duration.ofMinutes(15)).minusSeconds(1));
        assertThat(justBeforeExpiry.parse(token, TokenType.ACCESS).candidateId()).isEqualTo(42L);

        JwtTokenProvider afterExpiry = providerAt(NOW.plus(Duration.ofMinutes(15)).plusSeconds(1));
        assertThatThrownBy(() -> afterExpiry.parse(token, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldRejectTokenFromDifferentIssuer() {
        JwtProperties otherIssuer = new JwtProperties(
                SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "someone-else");
        String token = new JwtTokenProvider(otherIssuer, Clock.fixed(NOW, ZoneOffset.UTC))
                .issueAccessToken(42L, "a@example.com").value();

        assertThatThrownBy(() -> providerAt(NOW).parse(token, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldRejectTokenWithInconsistentOrMissingClaims() {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtTokenProvider provider = providerAt(NOW);

        // sub and cid disagree
        String mismatched = sign(encoder, JwtClaimsSet.builder()
                .issuer("job-intelligence").subject("1").claim("cid", 2L)
                .claim("email", "a@example.com").claim("type", "access")
                .id("jti-1").issuedAt(NOW).expiresAt(NOW.plusSeconds(600)));
        assertThatThrownBy(() -> provider.parse(mismatched, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);

        // email missing
        String noEmail = sign(encoder, JwtClaimsSet.builder()
                .issuer("job-intelligence").subject("1").claim("cid", 1L)
                .claim("type", "access")
                .id("jti-2").issuedAt(NOW).expiresAt(NOW.plusSeconds(600)));
        assertThatThrownBy(() -> provider.parse(noEmail, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);

        // type missing
        String noType = sign(encoder, JwtClaimsSet.builder()
                .issuer("job-intelligence").subject("1").claim("cid", 1L)
                .claim("email", "a@example.com")
                .id("jti-3").issuedAt(NOW).expiresAt(NOW.plusSeconds(600)));
        assertThatThrownBy(() -> provider.parse(noType, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);

        // unknown type
        String unknownType = sign(encoder, JwtClaimsSet.builder()
                .issuer("job-intelligence").subject("1").claim("cid", 1L)
                .claim("email", "a@example.com").claim("type", "admin")
                .id("jti-4").issuedAt(NOW).expiresAt(NOW.plusSeconds(600)));
        assertThatThrownBy(() -> provider.parse(unknownType, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldRejectGarbageAndBlankTokens() {
        JwtTokenProvider provider = providerAt(NOW);

        assertThatThrownBy(() -> provider.parse("not-a-jwt", TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> provider.parse("", TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> provider.parse(null, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
        // alg=none
        String unsigned = "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.";
        assertThatThrownBy(() -> provider.parse(unsigned, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldFailFastOnMissingOrWeakSecret() {
        Duration d = Duration.ofMinutes(1);
        assertThatThrownBy(() -> new JwtProperties(null, d, d, "i"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("  ", d, d, "i"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("${JWT_SECRET}-padding-padding-padding-padding", d, d, "i"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("too-short", d, d, "i"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ZERO, d, "i"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotExposeSecretInToString() {
        assertThat(properties.toString()).doesNotContain(SECRET);
    }

    private static String sign(NimbusJwtEncoder encoder, JwtClaimsSet.Builder claims) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
