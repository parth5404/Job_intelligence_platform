package com.samyak.job_intelligence.job.service.matching;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.domain.CandidateSkill;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import com.samyak.job_intelligence.candidate.repository.CandidateSkillRepository;
import com.samyak.job_intelligence.company.domain.Company;
import com.samyak.job_intelligence.company.repository.CompanyRepository;
import com.samyak.job_intelligence.job.domain.*;
import com.samyak.job_intelligence.job.repository.JobRepository;
import com.samyak.job_intelligence.job.repository.JobRequirementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
@Transactional
@Import(TestcontainersConfiguration.class)
class SkillMatchingServiceIT {

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
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobRequirementRepository jobRequirementRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CandidateSkillRepository candidateSkillRepository;

    @Autowired
    private SkillMatchingService skillMatchingService;

    @Test
    void shouldMatchCandidateSkillsAgainstJobRequirements() {

        // -------------------------------------------------
        // Company
        // -------------------------------------------------

        Company company = companyRepository.save(
                new Company(
                        "example company",
                        "Example Company",
                        "https://example.com"
                )
        );

        // -------------------------------------------------
        // Job
        // -------------------------------------------------

        Job job = jobRepository.save(
                new Job(
                        company,
                        "Backend Software Engineer",
                        "backend software engineer",
                        "Backend engineering role.",
                        EmploymentType.FULL_TIME,
                        SeniorityLevel.MID,
                        new BigDecimal("2"),
                        new BigDecimal("5"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "https://example.com/jobs/123",
                        "fingerprint-skill-test-123",
                        "description-hash-skill-test-123"
                )
        );

        // -------------------------------------------------
        // Job requirements
        // -------------------------------------------------

        jobRequirementRepository.save(
                new JobRequirement(
                        job,
                        RequirementType.TECHNOLOGY,
                        "Java",
                        "java",
                        true,
                        null,
                        "Experience with Java",
                        UUID.randomUUID().toString(),
                        RequirementMatchMode.SINGLE

                        )
        );

        jobRequirementRepository.save(
                new JobRequirement(
                        job,
                        RequirementType.TECHNOLOGY,
                        "Spring Boot",
                        "spring boot",
                        true,
                        null,
                        "Experience with Spring Boot",
                        UUID.randomUUID().toString(),
                        RequirementMatchMode.SINGLE
                        )
        );

        jobRequirementRepository.save(
                new JobRequirement(
                        job,
                        RequirementType.TECHNOLOGY,
                        "Kafka",
                        "kafka",
                        false,
                        null,
                        "Knowledge of Kafka",
                        UUID.randomUUID().toString(),
                        RequirementMatchMode.SINGLE
                        )
        );

        // -------------------------------------------------
        // Candidate
        // -------------------------------------------------

        CandidateProfile candidate = candidateProfileRepository.save(
                new CandidateProfile(
                        "Samyak",
                        "samyak-skill-test@example.com",
                        "test-password-hash",
                        "Bhopal",
                        null,   // preferred locations// preferred employment types
                        null,   // minimum salary
                        null,   // salary currency
                        true,
                        new BigDecimal("2"),
                        null
                )
        );

        // -------------------------------------------------
        // Candidate skills
        // -------------------------------------------------

        candidateSkillRepository.save(
                new CandidateSkill(
                        candidate,
                        "Java",
                        "java",
                        "EXPERT",
                        new BigDecimal("2.0"),
                        true
                )
        );

        candidateSkillRepository.save(
                new CandidateSkill(
                        candidate,
                        "Spring Boot",
                        "spring boot",
                        "INTERMEDIATE",
                        new BigDecimal("1.5"),
                        true
                )
        );

        candidateSkillRepository.save(
                new CandidateSkill(
                        candidate,
                        "React",
                        "react",
                        "BEGINNER",
                        new BigDecimal("1.0"),
                        false
                )
        );

        // -------------------------------------------------
        // Execute matching
        // -------------------------------------------------

        SkillMatchResult result =
                skillMatchingService.match(
                        candidate.getId(),
                        job.getId()
                );

        // -------------------------------------------------
        // Verify
        // -------------------------------------------------

        assertEquals(
                List.of("java", "spring boot"),
                result.matchedSkill()
        );

        assertEquals(
                List.of("kafka"),
                result.missingSkill()
        );

        assertEquals(
                0,
                result.missingMandatorySkills().size()
        );

        assertEquals(
                3,
                result.totalRequirements()
        );

        assertEquals(
                2,
                result.mandatoryRequirements()
        );
    }
}