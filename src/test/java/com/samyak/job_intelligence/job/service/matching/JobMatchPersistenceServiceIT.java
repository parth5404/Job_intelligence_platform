package com.samyak.job_intelligence.job.service.matching;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import com.samyak.job_intelligence.company.domain.Company;
import com.samyak.job_intelligence.company.repository.CompanyRepository;
import com.samyak.job_intelligence.job.domain.EmploymentType;
import com.samyak.job_intelligence.job.domain.Job;
import com.samyak.job_intelligence.job.domain.JobMatch;
import com.samyak.job_intelligence.job.domain.SeniorityLevel;
import com.samyak.job_intelligence.job.repository.JobMatchRepository;
import com.samyak.job_intelligence.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class JobMatchPersistenceServiceIT {

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
    private JobMatchPersistenceService jobMatchPersistenceService;

    @Autowired
    private JobMatchRepository jobMatchRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Test
    void shouldPersistNewJobMatch() {

        Company company = companyRepository.save(
                new Company(
                        "google",
                        "Google",
                        "https://www.google.com"
                )
        );

        Job job = jobRepository.save(
                new Job(
                        company,
                        "Backend Engineer",
                        "backend engineer",
                        "Build backend services using Java and Spring Boot.",
                        EmploymentType.FULL_TIME,
                        SeniorityLevel.ENTRY,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(2),
                        BigDecimal.valueOf(800000),
                        BigDecimal.valueOf(1200000),
                        "INR",
                        Instant.now(),
                        null,
                        "https://example.com/jobs/123",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                )
        );

        CandidateProfile candidateProfile =
                candidateProfileRepository.save(
                        new CandidateProfile(
                                "Samyak",
                                "samyak@example.com",
                                "test-password-hash",
                                "Indore",
                                null,
                                BigDecimal.valueOf(800000),
                                "INR",
                                true,
                                BigDecimal.valueOf(1),
                                null
                        )
                );

        JobMatchResult matchResult =
                new JobMatchResult(
                        true,
                        List.of(),
                        new SkillMatchScore(
                                new BigDecimal("0.8500"),
                                new BigDecimal("0.8000"),
                                new BigDecimal("0.8833"),
                                false
                        ),
                        new SkillMatchResult(
                                List.of("java"),
                                List.of("spring boot", "docker"),
                                List.of("java", "spring boot"),
                                List.of("spring boot"),
                                3,
                                2,
                                1,
                                1
                        ),
                        new JobOverallScore(new BigDecimal("85.00"))
                );

        Instant evaluatedAt = Instant.now();

        JobMatch saved =
                jobMatchPersistenceService.save(
                        job.getId(),
                        candidateProfile.getId(),
                        matchResult,
                        evaluatedAt
                );

        assertThat(saved.getId()).isNotNull();

        JobMatch retrieved =
                jobMatchRepository
                        .findByJobIdAndCandidateProfileId(
                                job.getId(),
                                candidateProfile.getId()
                        )
                        .orElseThrow();

        assertThat(retrieved.getJob().getId())
                .isEqualTo(job.getId());

        assertThat(retrieved.getCandidateProfile().getId())
                .isEqualTo(candidateProfile.getId());

        assertThat(retrieved.isHardQualified())
                .isTrue();

        assertThat(retrieved.getSkillScore())
                .isEqualByComparingTo("85.00");

        assertThat(retrieved.getFinalScore())
                .isEqualByComparingTo("85.00");

        assertThat(retrieved.getMatchReasoning())
                .isEqualTo(
                        "Matched skills: java; Missing skills: spring boot, docker"
                );

        assertThat(retrieved.getMandatorySkills())
                .containsExactly("java", "spring boot");

        assertThat(retrieved.getMissingMandatorySkills())
                .containsExactly("spring boot");

        assertThat(retrieved.getEvaluatedAt())
                .isEqualTo(evaluatedAt);
    }

    @Test
    void shouldUpdateExistingJobMatch() {

        Company company = companyRepository.save(
                new Company(
                        "microsoft",
                        "Microsoft",
                        "https://www.microsoft.com"
                )
        );

        Job job = jobRepository.save(
                new Job(
                        company,
                        "Software Engineer",
                        "software engineer",
                        "Build software applications.",
                        EmploymentType.FULL_TIME,
                        SeniorityLevel.ENTRY,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(2),
                        BigDecimal.valueOf(800000),
                        BigDecimal.valueOf(1200000),
                        "INR",
                        Instant.now(),
                        null,
                        "https://example.com/jobs/456",
                        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                )
        );

        CandidateProfile candidateProfile =
                candidateProfileRepository.save(
                        new CandidateProfile(
                                "Samyak",
                                "samyak-update@example.com",
                                "test-password-hash",
                                "Indore",
                                null,
                                BigDecimal.valueOf(800000),
                                "INR",
                                true,
                                BigDecimal.valueOf(1),
                                null
                        )
                );

        Instant firstEvaluation =
                Instant.now().minusSeconds(100);

        JobMatchResult firstResult =
                new JobMatchResult(
                        true,
                        List.of(),
                        new SkillMatchScore(
                                new BigDecimal("0.7000"),
                                new BigDecimal("0.7000"),
                                BigDecimal.ONE,
                                false
                        ),
                        new SkillMatchResult(
                                List.of("java"),
                                List.of("docker"),
                                List.of("java", "docker"),
                                List.of("docker"),
                                2,
                                1,
                                1,
                                0
                        ),
                        new JobOverallScore(new BigDecimal("70.00"))
                );

        JobMatch firstSaved =
                jobMatchPersistenceService.save(
                        job.getId(),
                        candidateProfile.getId(),
                        firstResult,
                        firstEvaluation
                );

        assertThat(firstSaved.getId()).isNotNull();

        assertThat(firstSaved.getMandatorySkills())
                .containsExactly("java", "docker");

        assertThat(firstSaved.getMissingMandatorySkills())
                .containsExactly("docker");

        assertThat(firstSaved.getFinalScore())
                .isEqualByComparingTo("70.00");

        Instant secondEvaluation = Instant.now();

        JobMatchResult secondResult =
                new JobMatchResult(
                        true,
                        List.of(),
                        new SkillMatchScore(
                                new BigDecimal("0.9000"),
                                new BigDecimal("0.9000"),
                                BigDecimal.ONE,
                                false
                        ),
                        new SkillMatchResult(
                                List.of("java", "docker"),
                                List.of(),
                                List.of("java", "docker"),
                                List.of(),
                                2,
                                2,
                                2,
                                2
                        ),
                        new JobOverallScore(new BigDecimal("90.00"))
                );

        JobMatch secondSaved =
                jobMatchPersistenceService.save(
                        job.getId(),
                        candidateProfile.getId(),
                        secondResult,
                        secondEvaluation
                );

        assertThat(secondSaved.getId())
                .isEqualTo(firstSaved.getId());

        JobMatch retrieved =
                jobMatchRepository
                        .findByJobIdAndCandidateProfileId(
                                job.getId(),
                                candidateProfile.getId()
                        )
                        .orElseThrow();

        assertThat(retrieved.getId())
                .isEqualTo(firstSaved.getId());

        assertThat(retrieved.getSkillScore())
                .isEqualByComparingTo("90.00");

        assertThat(retrieved.getFinalScore())
                .isEqualByComparingTo("90.00");

        assertThat(retrieved.getMatchReasoning())
                .isEqualTo(
                        "Matched skills: java, docker; Missing skills: "
                );

        assertThat(retrieved.getMandatorySkills())
                .containsExactly("java", "docker");

        assertThat(retrieved.getMissingMandatorySkills())
                .isEmpty();

        assertThat(retrieved.getEvaluatedAt())
                .isEqualTo(secondEvaluation);
    }
}