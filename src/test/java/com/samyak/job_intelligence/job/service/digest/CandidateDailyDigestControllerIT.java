package com.samyak.job_intelligence.job.service.digest;

import com.samyak.job_intelligence.auth.security.AuthenticatedCandidate;
import com.samyak.job_intelligence.company.domain.Company;
import com.samyak.job_intelligence.job.domain.Job;
import com.samyak.job_intelligence.job.domain.JobMatch;
import com.samyak.job_intelligence.job.service.digest.DailyDigest;
import com.samyak.job_intelligence.job.service.digest.DailyDigestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CandidateDailyDigestControllerIT {

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

    @MockitoBean
    private DailyDigestService dailyDigestService;

    @Test
    void shouldReturnDailyDigestForCandidate() throws Exception {

        Long candidateProfileId = 10L;
        int limit = 2;

        Instant generatedAt =
                Instant.parse("2026-09-12T10:00:00Z");

        Company company = mock(Company.class);

        Job job1 = mock(Job.class);
        Job job2 = mock(Job.class);

        JobMatch match1 = mock(JobMatch.class);
        JobMatch match2 = mock(JobMatch.class);

        when(company.getDisplayName())
                .thenReturn("Google");

        when(job1.getId())
                .thenReturn(101L);
        when(job1.getTitle())
                .thenReturn("Backend Engineer");
        when(job1.getCompany())
                .thenReturn(company);
        when(job1.getCanonicalApplicationUrl())
                .thenReturn("https://example.com/jobs/101");

        when(job2.getId())
                .thenReturn(102L);
        when(job2.getTitle())
                .thenReturn("Java Engineer");
        when(job2.getCompany())
                .thenReturn(company);
        when(job2.getCanonicalApplicationUrl())
                .thenReturn("https://example.com/jobs/102");

        when(match1.getJob())
                .thenReturn(job1);
        when(match1.getFinalScore())
                .thenReturn(new BigDecimal("92.00"));
        when(match1.getMatchReasoning())
                .thenReturn("Strong backend match");

        when(match2.getJob())
                .thenReturn(job2);
        when(match2.getFinalScore())
                .thenReturn(new BigDecimal("87.00"));
        when(match2.getMatchReasoning())
                .thenReturn("Good Java match");

        DailyDigest digest =
                new DailyDigest(
                        candidateProfileId,
                        generatedAt,
                        List.of(match1, match2)
                );

        when(dailyDigestService.generate(
                candidateProfileId,
                limit
        )).thenReturn(digest);

        mockMvc.perform(
                        get(
                                "/api/v1/candidates/{id}/daily-digest",
                                candidateProfileId
                        )
                                .param("limit", String.valueOf(limit))
                                .with(asCandidate(10L))
                )
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.candidateProfileId")
                        .value(10))

                .andExpect(jsonPath("$.generatedAt")
                        .value("2026-09-12T10:00:00Z"))

                .andExpect(jsonPath("$.jobs.length()")
                        .value(2))

                // Ranking order
                .andExpect(jsonPath("$.jobs[0].jobId")
                        .value(101))
                .andExpect(jsonPath("$.jobs[0].title")
                        .value("Backend Engineer"))
                .andExpect(jsonPath("$.jobs[0].companyName")
                        .value("Google"))
                .andExpect(jsonPath("$.jobs[0].finalScore")
                        .value(92.00))
                .andExpect(jsonPath("$.jobs[0].matchReasoning")
                        .value("Strong backend match"))
                .andExpect(jsonPath("$.jobs[0].applicationUrl")
                        .value("https://example.com/jobs/101"))

                .andExpect(jsonPath("$.jobs[1].jobId")
                        .value(102))
                .andExpect(jsonPath("$.jobs[1].title")
                        .value("Java Engineer"))
                .andExpect(jsonPath("$.jobs[1].companyName")
                        .value("Google"))
                .andExpect(jsonPath("$.jobs[1].finalScore")
                        .value(87.00))
                .andExpect(jsonPath("$.jobs[1].matchReasoning")
                        .value("Good Java match"))
                .andExpect(jsonPath("$.jobs[1].applicationUrl")
                        .value("https://example.com/jobs/102"));

        verify(dailyDigestService)
                .generate(eq(candidateProfileId), eq(limit));
    }

    @Test
    void shouldRejectLimitAboveMaximum() throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/candidates/{id}/daily-digest",
                                10L
                        )
                                .param("limit", "101")
                                .with(asCandidate(10L))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectLimitBelowMinimum() throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/candidates/{id}/daily-digest",
                                10L
                        )
                                .param("limit", "0")
                                .with(asCandidate(10L))
                )
                .andExpect(status().isBadRequest());
    }

    /** Authenticates the request as candidate {@code id}, as the JWT filter would. */
    private static RequestPostProcessor asCandidate(long id) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedCandidate(id, "candidate" + id + "@example.com"),
                null,
                List.of()
        ));
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/candidates/{id}/daily-digest", 10L)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dailyDigestService);
    }

    @Test
    void shouldRejectAnotherCandidatesRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/candidates/{id}/daily-digest", 11L)
                                .with(asCandidate(10L))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(dailyDigestService);
    }

    @Test
    void shouldRejectAnotherCandidatesRequestBeforeValidatingParameters() throws Exception {
        mockMvc.perform(
                        get("/api/v1/candidates/{id}/daily-digest", 11L)
                                .param("limit", "0")
                                .with(asCandidate(10L))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(dailyDigestService);
    }
}
