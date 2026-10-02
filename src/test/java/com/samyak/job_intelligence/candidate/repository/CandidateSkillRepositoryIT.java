package com.samyak.job_intelligence.candidate.repository;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.domain.CandidateSkill;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
@Transactional
@Import(TestcontainersConfiguration.class)
class CandidateSkillRepositoryIT {

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
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CandidateSkillRepository candidateSkillRepository;

    @Test
    void shouldFindSkillsByCandidateProfileId() {

        CandidateProfile candidate = candidateProfileRepository.save(
                new CandidateProfile(
                        "Samyak",
                        "samyak@example.com",
                        "test-password-hash",
                        "Bhopal",
                        null,
                        null,
                        null,
                        true,
                        new BigDecimal("2"),
                        null
                )
        );

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

        List<CandidateSkill> skills =
                candidateSkillRepository.findByCandidateProfileId(
                        candidate.getId()
                );

        assertEquals(2, skills.size());
    }

    @Test
    void shouldFindSkillsByCandidateProfileIdAndNormalizedSkillIn() {

        CandidateProfile candidate = candidateProfileRepository.save(
                new CandidateProfile(
                        "Samyak",
                        "samyak2@example.com",
                        "test-password-hash",
                        "Bhopal",
                        null,
                        null,
                        null,
                        true,
                        new BigDecimal("2"),
                        null
                )
        );

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

        List<CandidateSkill> matchingSkills =
                candidateSkillRepository
                        .findByCandidateProfile_IdAndNormalizedSkillIn(
                                candidate.getId(),
                                List.of("java", "spring boot", "kafka")
                        );

        assertEquals(2, matchingSkills.size());
    }
}