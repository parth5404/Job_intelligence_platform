package com.samyak.job_intelligence.candidate;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.domain.CandidateSkill;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import com.samyak.job_intelligence.candidate.repository.CandidateSkillRepository;
import com.samyak.job_intelligence.candidate.service.CandidateProfileService;
import com.samyak.job_intelligence.company.domain.Company;
import com.samyak.job_intelligence.company.repository.CompanyRepository;
import com.samyak.job_intelligence.job.domain.*;
import com.samyak.job_intelligence.job.repository.JobLocationRepository;
import com.samyak.job_intelligence.job.repository.JobMatchRepository;
import com.samyak.job_intelligence.job.repository.JobRepository;
import com.samyak.job_intelligence.job.repository.JobRequirementRepository;
import com.samyak.job_intelligence.job.service.matching.CandidateJobMatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class CandidateMatchingIT {

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
    private CandidateProfileService candidateProfileService;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CandidateSkillRepository candidateSkillRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobLocationRepository jobLocationRepository;

    @Autowired
    private JobRequirementRepository jobRequirementRepository;

    @Autowired
    private JobMatchRepository jobMatchRepository;

    @Autowired
    private CandidateJobMatchingService candidateJobMatchingService;

    @Test
    void shouldCreateCandidateAddSkillsEvaluateAndRankJobs() {

        /*
         * 1. Create candidate
         */

        CandidateProfile candidate =
                candidateProfileService.create(
                        "Test Candidate",
                        "candidate@test.com",
                        "test-password-hash",
                        "Bangalore",
                        JsonNodeFactory.instance
                                .arrayNode()
                                .add("Bangalore"),
                        new BigDecimal("60000"),
                        "INR",
                        new BigDecimal("2.0"),
                        JsonNodeFactory.instance
                                .arrayNode()
                                .add("FULL_TIME")
                );

        assertThat(candidate.getId())
                .isNotNull();

        /*
         * 2. Add candidate skills
         */

        candidateSkillRepository.save(
                new CandidateSkill(
                        candidate,
                        "Java",
                        "java",
                        "ADVANCED",
                        new BigDecimal("2.0"),
                        true
                )
        );

        candidateSkillRepository.save(
                new CandidateSkill(
                        candidate,
                        "Spring",
                        "spring",
                        "ADVANCED",
                        new BigDecimal("2.0"),
                        true
                )
        );

        List<CandidateSkill> candidateSkills =
                candidateSkillRepository
                        .findByCandidateProfileId(candidate.getId());

        assertThat(candidateSkills)
                .hasSize(2);

        assertThat(candidateSkills)
                .extracting(CandidateSkill::getNormalizedSkill)
                .containsExactlyInAnyOrder(
                        "java",
                        "spring"
                );

        /*
         * 3. Create company
         */

        Company company =
                companyRepository.save(
                        new Company(
                                "test-company",
                                "Test Company",
                                "https://example.com"
                        )
                );

        /*
         * 4. Create a job that should qualify
         */

        Job qualifiedJob =
                new Job(
                        company,
                        "Backend Engineer",
                        "backend engineer",
                        "Backend engineering role using Java and Spring.",
                        EmploymentType.FULL_TIME,
                        SeniorityLevel.MID,
                        new BigDecimal("1.0"),
                        new BigDecimal("5.0"),
                        new BigDecimal("60000"),
                        new BigDecimal("120000"),
                        "INR",
                        Instant.now(),
                        null,
                        "https://example.com/jobs/backend",
                        "qualified-job-fingerprint",
                        "qualified-job-description-hash"
                );

        qualifiedJob.markSeen(Instant.now());

        qualifiedJob =
                jobRepository.save(qualifiedJob);

        jobLocationRepository.save(
                new JobLocation(
                        qualifiedJob,
                        "Bangalore",
                        "Karnataka",
                        "India",
                        RemoteType.UNKNOWN,
                        "Bangalore, Karnataka, India"
                )
        );

        jobRequirementRepository.save(
                new JobRequirement(
                        qualifiedJob,
                        RequirementType.TECHNOLOGY,
                        "Java",
                        "java",
                        true,
                        new BigDecimal("1.0"),
                        "Java experience is required.",
                        UUID.randomUUID().toString(),
                        RequirementMatchMode.SINGLE
                )
        );

        jobRequirementRepository.save(
                new JobRequirement(
                        qualifiedJob,
                        RequirementType.TECHNOLOGY,
                        "Spring",
                        "spring",
                        true,
                        new BigDecimal("1.0"),
                        "Spring experience is required.",
                        UUID.randomUUID().toString(),
                        RequirementMatchMode.SINGLE

                )
        );

        /*
         * 5. Create a job that should be rejected
         *    because the location does not match.
         */

        Job rejectedJob =
                new Job(
                        company,
                        "Backend Engineer",
                        "backend engineer",
                        "Backend engineering role using Java and Spring.",
                        EmploymentType.FULL_TIME,
                        SeniorityLevel.MID,
                        new BigDecimal("1.0"),
                        new BigDecimal("5.0"),
                        new BigDecimal("60000"),
                        new BigDecimal("120000"),
                        "INR",
                        Instant.now(),
                        null,
                        "https://example.com/jobs/backend-delhi",
                        "rejected-job-fingerprint",
                        "rejected-job-description-hash"
                );

        rejectedJob.markSeen(Instant.now());

        rejectedJob =
                jobRepository.save(rejectedJob);

        jobLocationRepository.save(
                new JobLocation(
                        rejectedJob,
                        "Delhi",
                        "Delhi",
                        "India",
                        RemoteType.UNKNOWN,
                        "Delhi, India"
                )
        );

        jobRequirementRepository.save(
                new JobRequirement(
                        rejectedJob,
                        RequirementType.TECHNOLOGY,
                        "Java",
                        "java",
                        true,
                        new BigDecimal("1.0"),
                        "Java experience is required.",
                        UUID.randomUUID().toString(),
                        RequirementMatchMode.SINGLE
                )
        );

        jobRequirementRepository.save(
                new JobRequirement(
                        rejectedJob,
                        RequirementType.TECHNOLOGY,
                        "Spring",
                        "spring",
                        true,
                        new BigDecimal("1.0"),
                        "Spring experience is required.",
                        UUID.randomUUID().toString(),
                        RequirementMatchMode.SINGLE
                )
        );

        /*
         * 6. Run the complete candidate matching flow
         */

        List<JobMatch> rankedMatches =
                candidateJobMatchingService.evaluateAndRank(
                        candidate.getId()
                );

        /*
         * 7. Only the qualified job should appear in ranking
         */

        assertThat(rankedMatches)
                .hasSize(1);

        assertThat(rankedMatches.getFirst().getJob().getId())
                .isEqualTo(qualifiedJob.getId());

        assertThat(rankedMatches.getFirst().isHardQualified())
                .isTrue();

        assertThat(rankedMatches.getFirst().getFinalScore())
                .isNotNull();

        /*
         * 8. Both matches should have been persisted
         */

        JobMatch qualifiedMatch =
                jobMatchRepository
                        .findByJobIdAndCandidateProfileId(
                                qualifiedJob.getId(),
                                candidate.getId()
                        )
                        .orElseThrow();

        JobMatch rejectedMatch =
                jobMatchRepository
                        .findByJobIdAndCandidateProfileId(
                                rejectedJob.getId(),
                                candidate.getId()
                        )
                        .orElseThrow();

        assertThat(qualifiedMatch.isHardQualified())
                .isTrue();

        assertThat(qualifiedMatch.getFinalScore())
                .isNotNull();

        assertThat(rejectedMatch.isHardQualified())
                .isFalse();

        assertThat(rejectedMatch.getMatchReasoning())
                .contains(
                        "Job location does not match candidate preferred locations"
                );
    }
}
