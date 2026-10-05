package com.samyak.job_intelligence.candidate.service;


import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.domain.CandidateSkill;
import com.samyak.job_intelligence.candidate.exception.CandidateSkillAlreadyExistsException;
import com.samyak.job_intelligence.candidate.normalization.CandidateTextNormalizer;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import com.samyak.job_intelligence.candidate.repository.CandidateSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class CandidateSkillServiceIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("job_intelligence_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    CandidateSkillService candidateSkillService;

    @Autowired
    CandidateProfileRepository candidateProfileRepository;

    @Autowired
    CandidateSkillRepository candidateSkillRepository;

    @Autowired
    CandidateTextNormalizer candidateTextNormalizer;

    @Test
    void shouldCreateNewSkill() {

        CandidateProfile candidate = new CandidateProfile(
                "Test Candidate",
                "test-skill-create@gmail.com",
                "test-password-hash",
                "Pune",
                null,
                new BigDecimal("800000"),
                "INR",
                true,
                new BigDecimal("1.0"),
                null
        );

        candidate = candidateProfileRepository.save(candidate);

        CandidateSkill skill = candidateSkillService.addSkill(
                candidate.getId(),
                "JavaScript",
                "Advanced",
                new BigDecimal("1.0"),
                true
        );

        assertThat(skill.getId()).isPositive();
        assertThat(skill.getSkill()).isEqualTo("JavaScript");
        assertThat(skill.getNormalizedSkill())
                .isEqualTo(candidateTextNormalizer.normalizeSkill("JavaScript"));
        assertThat(skill.getCandidateProfile().getId())
                .isEqualTo(candidate.getId());
    }

    @Test
    void shouldRejectDuplicateNormalizedSkill() {

        CandidateProfile candidate = new CandidateProfile(
                "Duplicate Test Candidate",
                "test-skill-duplicate@gmail.com",
                "test-password-hash",
                "Pune",
                null,
                new BigDecimal("800000"),
                "INR",
                true,
                new BigDecimal("1.0"),
                null
        );

        candidate = candidateProfileRepository.save(candidate);

        candidateSkillService.addSkill(
                candidate.getId(),
                "JavaScript",
                "Advanced",
                new BigDecimal("1.0"),
                true
        );

        Long candidateId = candidate.getId();

        assertThatThrownBy(() ->
                candidateSkillService.addSkill(
                        candidateId,
                        "JavaScript",
                        "Advanced",
                        new BigDecimal("1.0"),
                        true
                )
        )
                .isInstanceOf(CandidateSkillAlreadyExistsException.class);

        assertThat(candidateSkillRepository
                .findByCandidateProfileId(candidateId))
                .hasSize(1);
    }
}
