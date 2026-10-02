package com.samyak.job_intelligence.auth.web;

import com.samyak.job_intelligence.auth.domain.CandidateRefreshToken;
import com.samyak.job_intelligence.auth.repository.CandidateRefreshTokenRepository;
import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

    private static final String PASSWORD = "correct-horse-battery";

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("job_intelligence_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CandidateRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------ registration

    @Test
    void shouldRegisterCandidateAndStoreBcryptHash() throws Exception {
        String email = uniqueEmail();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.candidate.id").isNumber())
                .andExpect(jsonPath("$.candidate.email").value(email))
                .andExpect(jsonPath("$.candidate.name").value("Auth Tester"))
                .andExpect(jsonPath("$.candidate.active").value(true))
                .andReturn();

        CandidateProfile stored = candidateProfileRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash())
                .isNotEqualTo(PASSWORD)
                .matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(stored.isEmailVerified()).isFalse();
        assertThat(stored.isActive()).isTrue();

        // Only the SHA-256 digest of the refresh token is stored, never the token itself.
        String refreshToken = json(result).get("refreshToken").asString();
        List<CandidateRefreshToken> tokens = refreshTokensOf(stored.getId());
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getTokenHash())
                .isEqualTo(sha256Hex(refreshToken))
                .isNotEqualTo(refreshToken)
                .hasSize(64);
        assertThat(tokens.getFirst().isRevoked()).isFalse();
    }

    @Test
    void shouldTrimAndLowercaseEmailOnRegistration() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("  " + email.toUpperCase() + "  ", PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidate.email").value(email));

        assertThat(candidateProfileRepository.findByEmail(email)).isPresent();
    }

    @Test
    void shouldRejectDuplicateEmailIncludingDifferentCaseAndWhitespace() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, PASSWORD, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(" " + email.toUpperCase() + " ", PASSWORD, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectPasswordMismatch() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, PASSWORD, "something-else-entirely")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.passwordConfirmed").value("Passwords do not match"));

        assertThat(candidateProfileRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void shouldRejectInvalidRegistrationBody() throws Exception {
        // short password, bad email, missing required fields
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "not-an-email", "password": "short", "confirmPassword": "short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.preferredLocations").exists())
                .andExpect(jsonPath("$.fieldErrors.experienceYears").exists())
                .andExpect(jsonPath("$.fieldErrors.preferredEmploymentTypes").exists());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectPasswordLongerThanBcryptLimitInBytes() throws Exception {
        // 40 characters, 80 bytes: passes @Size(max = 72) but BCrypt would silently truncate it.
        String multiByte = "é".repeat(40);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), multiByte, multiByte)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ login

    @Test
    void shouldLoginWithValidCredentials() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("  " + email.toUpperCase() + " ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.candidate.email").value(email));
    }

    @Test
    void shouldReturnIdenticalUnauthorizedResponseForUnknownEmailAndWrongPassword() throws Exception {
        String email = uniqueEmail();
        register(email);

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "definitely-the-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode first = json(wrongPassword);
        JsonNode second = json(unknownEmail);
        assertThat(first.get("status").asInt()).isEqualTo(401);
        assertThat(first.get("error").asString()).isEqualTo(second.get("error").asString());
        assertThat(first.get("message").asString()).isEqualTo(second.get("message").asString());
        assertThat(first.has("fieldErrors")).isFalse();
        assertThat(wrongPassword.getResponse().getContentAsString())
                .doesNotContain("password", "email", "Exception", "at com.");
    }

    @Test
    void shouldNotLogInInactiveCandidateOrCandidateWithoutUsablePassword() throws Exception {
        // A legacy row as the V26 migration leaves it: sentinel password hash.
        String legacyEmail = uniqueEmail();
        candidateProfileRepository.save(new CandidateProfile(
                "Legacy", legacyEmail, "!", null, null, null, null, true, null, null));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(legacyEmail, "!")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(legacyEmail, PASSWORD)))
                .andExpect(status().isUnauthorized());

        String inactiveEmail = uniqueEmail();
        candidateProfileRepository.save(new CandidateProfile(
                "Inactive", inactiveEmail, passwordEncoder.encode(PASSWORD),
                null, null, null, null, false, null, null));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(inactiveEmail, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidLoginBodyWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ me

    @Test
    void shouldReturnCurrentCandidateFromMe() throws Exception {
        String email = uniqueEmail();
        JsonNode registered = register(email);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + registered.get("accessToken").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registered.get("candidate").get("id").asLong()))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void shouldRejectMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ------------------------------------------------------------------ refresh / logout

    @Test
    void shouldRotateRefreshTokenAndRejectReuse() throws Exception {
        JsonNode registered = register(uniqueEmail());
        String firstRefresh = registered.get("refreshToken").asString();
        long candidateId = registered.get("candidate").get("id").asLong();

        JsonNode rotated = json(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn());

        String secondRefresh = rotated.get("refreshToken").asString();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);
        assertThat(rotated.get("accessToken").asString())
                .isNotEqualTo(registered.get("accessToken").asString());

        // The new access token works.
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + rotated.get("accessToken").asString()))
                .andExpect(status().isOk());

        // Old token is revoked; the new one is active.
        List<CandidateRefreshToken> tokens = refreshTokensOf(candidateId);
        assertThat(tokens).hasSize(2);
        assertThat(tokens).filteredOn(CandidateRefreshToken::isRevoked).hasSize(1);

        // Replaying the old refresh token is rejected AND revokes the whole token family.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefresh)))
                .andExpect(status().isUnauthorized());

        assertThat(refreshTokensOf(candidateId)).allMatch(CandidateRefreshToken::isRevoked);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(secondRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAccessTokenAndGarbageOnRefreshEndpoint() throws Exception {
        JsonNode registered = register(uniqueEmail());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(registered.get("accessToken").asString())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("garbage")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldIgnoreStaleAuthorizationHeaderOnPublicEndpoints() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer an.expired.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRevokeRefreshTokensOnLogout() throws Exception {
        String email = uniqueEmail();
        JsonNode registered = register(email);
        // A second session (device) for the same candidate.
        JsonNode secondSession = json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn());
        long candidateId = registered.get("candidate").get("id").asLong();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + registered.get("accessToken").asString()))
                .andExpect(status().isNoContent());

        assertThat(refreshTokensOf(candidateId)).hasSize(2).allMatch(CandidateRefreshToken::isRevoked);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(registered.get("refreshToken").asString())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(secondSession.get("refreshToken").asString())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireAuthenticationForLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotLogOutOtherCandidates() throws Exception {
        JsonNode first = register(uniqueEmail());
        JsonNode second = register(uniqueEmail());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + first.get("accessToken").asString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(second.get("refreshToken").asString())))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ no sensitive data in JSON

    @Test
    void shouldNeverSerializeSensitiveFields() throws Exception {
        String email = uniqueEmail();

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode registered = json(registerResult);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(registered.get("refreshToken").asString())))
                .andExpect(status().isOk())
                .andReturn();

        String bearer = "Bearer " + registered.get("accessToken").asString();
        MvcResult meResult = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        MvcResult candidatesMeResult = mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        MvcResult duplicateResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, PASSWORD, PASSWORD)))
                .andExpect(status().isConflict()).andReturn();

        String storedHash = candidateProfileRepository.findByEmail(email).orElseThrow().getPasswordHash();

        for (MvcResult result : List.of(registerResult, loginResult, refreshResult, meResult,
                candidatesMeResult, duplicateResult)) {
            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain(storedHash, PASSWORD);
            assertNoSensitiveKeys(objectMapper.readTree(body));
        }
    }

    private void assertNoSensitiveKeys(JsonNode node) {
        List<String> forbidden = List.of("password", "passwordHash", "password_hash", "confirmPassword",
                "refreshTokenHash", "tokenHash", "token_hash", "emailVerified", "email_verified");
        if (node.isObject()) {
            node.propertyNames().forEach(name -> {
                assertThat(forbidden).doesNotContain(name);
                assertNoSensitiveKeys(node.get(name));
            });
        } else if (node.isArray()) {
            node.forEach(this::assertNoSensitiveKeys);
        }
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode register(String email) throws Exception {
        return json(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<CandidateRefreshToken> refreshTokensOf(Long candidateId) {
        return refreshTokenRepository.findAll().stream()
                .filter(token -> token.getCandidateProfile().getId().equals(candidateId))
                .toList();
    }

    private static String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }

    private static String registerBody(String email, String password, String confirmPassword) {
        return """
                {
                    "name": "Auth Tester",
                    "email": "%s",
                    "password": "%s",
                    "confirmPassword": "%s",
                    "currentLocation": "Bangalore",
                    "preferredLocations": ["Bangalore"],
                    "minimumSalary": 1000000,
                    "minimumSalaryCurrency": "INR",
                    "experienceYears": 2.5,
                    "preferredEmploymentTypes": ["FULL_TIME"]
                }
                """.formatted(email, password, confirmPassword);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\": \"%s\"}".formatted(refreshToken);
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
