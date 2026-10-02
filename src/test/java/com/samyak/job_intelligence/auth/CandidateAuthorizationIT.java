package com.samyak.job_intelligence.auth;

import com.samyak.job_intelligence.auth.config.JwtProperties;
import com.samyak.job_intelligence.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CandidateAuthorizationIT {

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
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Registered(long id, String email, String accessToken, String refreshToken) {
    }

    // ------------------------------------------------------------------ authentication of requests

    @Test
    void shouldRejectProtectedRoutesWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/candidates/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication failed"));

        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectMalformedToken() throws Exception {
        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTokenWithInvalidSignature() throws Exception {
        Registered candidate = register();
        JwtProperties otherSecret = new JwtProperties(
                "another-secret-which-is-also-long-enough-0123456789",
                Duration.ofMinutes(15), Duration.ofDays(7), jwtProperties.issuer());
        String forged = new JwtTokenProvider(otherSecret, Clock.systemUTC())
                .issueAccessToken(candidate.id(), candidate.email()).value();

        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        Registered candidate = register();
        Clock longAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), java.time.ZoneOffset.UTC);
        String expired = new JwtTokenProvider(jwtProperties, longAgo)
                .issueAccessToken(candidate.id(), candidate.email()).value();

        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRefreshTokenUsedAsAccessToken() throws Exception {
        Registered candidate = register();

        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer " + candidate.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTokenWhoseEmailNoLongerMatchesOrCandidateIsGoneOrInactive() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(jwtProperties, Clock.systemUTC());
        Registered candidate = register();

        // Correctly signed but for a different email than the candidate currently has.
        String wrongEmail = provider.issueAccessToken(candidate.id(), "someone-else@example.com").value();
        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer " + wrongEmail))
                .andExpect(status().isUnauthorized());

        // Correctly signed for a candidate that does not exist.
        String unknownCandidate = provider.issueAccessToken(987_654_321L, candidate.email()).value();
        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer " + unknownCandidate))
                .andExpect(status().isUnauthorized());

        // Valid token stops working once the candidate is deactivated.
        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer " + candidate.accessToken()))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE candidate_profiles SET is_active = FALSE WHERE id = ?", candidate.id());
        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer " + candidate.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldKeepHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotAcceptAnonymousRegistrationRoutesViaOtherMethods() throws Exception {
        // Only POST is public on the auth routes.
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNoLongerExposeUnauthenticatedCandidateCreation() throws Exception {
        mockMvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ own resources

    @Test
    void shouldReadAndUpdateOwnProfileViaMe() throws Exception {
        Registered candidate = register();

        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidate.id()))
                .andExpect(jsonPath("$.email").value(candidate.email()));

        mockMvc.perform(patch("/api/v1/candidates/me")
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed Via Me\", \"currentLocation\": \"Pune\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Via Me"))
                .andExpect(jsonPath("$.currentLocation").value("Pune"))
                .andExpect(jsonPath("$.id").value(candidate.id()));
    }

    @Test
    void shouldUpdateOwnProfileById() throws Exception {
        Registered candidate = register();

        mockMvc.perform(patch("/api/v1/candidates/{id}", candidate.id())
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed By Id\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed By Id"));
    }

    @Test
    void shouldNormalizeEmailAndRejectDuplicateWhenUpdatingProfile() throws Exception {
        Registered candidate = register();
        Registered other = register();

        mockMvc.perform(patch("/api/v1/candidates/me")
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"  " + other.email().toUpperCase() + " \"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldInvalidateAccessTokenAfterEmailChangeButAllowRefresh() throws Exception {
        Registered candidate = register();
        String newEmail = "changed-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(patch("/api/v1/candidates/me")
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + newEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));

        // The access token was issued for the old email and no longer authenticates.
        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", bearer(candidate)))
                .andExpect(status().isUnauthorized());

        // The refresh token still works and yields tokens for the new email.
        String response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + candidate.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.email").value(newEmail))
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = objectMapper.readTree(response).get("accessToken").asString();

        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAddOwnSkillsDirectlyAndInBulk() throws Exception {
        Registered candidate = register();

        mockMvc.perform(post("/api/v1/candidates/{id}/skills", candidate.id())
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skill\": \"Java\", \"proficiency\": \"ADVANCED\", \"yearsExperience\": 3, \"primary\": true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/candidates/{id}/skills/bulk", candidate.id())
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\": [{\"skill\": \"Spring\", \"primary\": false}]}"))
                .andExpect(status().isCreated());

        // Adding the same skill twice is a conflict, not a server error.
        mockMvc.perform(post("/api/v1/candidates/{id}/skills", candidate.id())
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skill\": \"Java\", \"primary\": false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReadOwnDailyDigestAndRefreshOwnMatching() throws Exception {
        Registered candidate = register();

        mockMvc.perform(get("/api/v1/candidates/{id}/daily-digest", candidate.id())
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateProfileId").value(candidate.id()))
                .andExpect(jsonPath("$.jobs").isArray());

        mockMvc.perform(post("/api/v1/candidates/{id}/matching/refresh", candidate.id())
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateProfileId").value(candidate.id()));
    }

    // ------------------------------------------------------------------ other candidates' resources

    @Test
    void shouldForbidAccessToAnotherCandidatesResources() throws Exception {
        Registered attacker = register();
        Registered victim = register();
        // A candidate id that does not exist must be indistinguishable from someone else's id.
        long missingId = 987_654_321L;

        for (long targetId : new long[]{victim.id(), missingId}) {
            forbidden(attacker, patch("/api/v1/candidates/{id}", targetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Hacked\"}"));

            forbidden(attacker, post("/api/v1/candidates/{id}/skills", targetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"skill\": \"Java\", \"primary\": false}"));

            forbidden(attacker, post("/api/v1/candidates/{id}/skills/bulk", targetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"skills\": [{\"skill\": \"Java\", \"primary\": false}]}"));

            forbidden(attacker, get("/api/v1/candidates/{id}/daily-digest", targetId));

            forbidden(attacker, post("/api/v1/candidates/{id}/matching/refresh", targetId));
        }

        // The victim's profile was not modified.
        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", bearer(victim)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Authz Tester"));
    }

    @Test
    void shouldNotTrustCandidateIdsInBodyOrQueryForAuthorization() throws Exception {
        Registered attacker = register();
        Registered victim = register();

        // /me always targets the authenticated candidate, whatever the body or query claims.
        mockMvc.perform(patch("/api/v1/candidates/me")
                        .param("candidateProfileId", String.valueOf(victim.id()))
                        .header("Authorization", bearer(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": " + victim.id() + ", \"candidateProfileId\": " + victim.id()
                                + ", \"name\": \"Only Me\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attacker.id()))
                .andExpect(jsonPath("$.name").value("Only Me"));

        mockMvc.perform(get("/api/v1/candidates/me").header("Authorization", bearer(victim)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Authz Tester"));
    }

    // ------------------------------------------------------------------ helpers

    private void forbidden(Registered caller, MockHttpServletRequestBuilder request) throws Exception {
        ResultActions result = mockMvc.perform(request.header("Authorization", bearer(caller)));
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    private static String bearer(Registered candidate) {
        return "Bearer " + candidate.accessToken();
    }

    private Registered register() throws Exception {
        String email = "authz-" + UUID.randomUUID() + "@example.com";
        String body = """
                {
                    "name": "Authz Tester",
                    "email": "%s",
                    "password": "%s",
                    "confirmPassword": "%s",
                    "currentLocation": "Bangalore",
                    "preferredLocations": ["Bangalore"],
                    "experienceYears": 2,
                    "preferredEmploymentTypes": ["FULL_TIME"]
                }
                """.formatted(email, PASSWORD, PASSWORD);

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return new Registered(
                json.get("candidate").get("id").asLong(),
                email,
                json.get("accessToken").asString(),
                json.get("refreshToken").asString()
        );
    }
}
