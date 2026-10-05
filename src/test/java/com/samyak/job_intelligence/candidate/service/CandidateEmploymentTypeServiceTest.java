package com.samyak.job_intelligence.candidate.service;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.job.domain.EmploymentType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CandidateEmploymentTypeServiceTest {

    private final CandidateEmploymentTypeService service =
            new CandidateEmploymentTypeService();

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void shouldExtractPreferredEmploymentTypes() throws Exception {

        JsonNode preferredTypes = objectMapper.readTree("""
                [
                    "FULL_TIME",
                    "CONTRACT"
                ]
                """);

        CandidateProfile candidate = new CandidateProfile(
                "Samyak",
                "samyak@example.com",
                "test-password-hash",
                "Bhopal",
                null,
                null,
                null,
                true,
                new BigDecimal("2"),
                preferredTypes
        );

        assertEquals(
                List.of(
                        EmploymentType.FULL_TIME,
                        EmploymentType.CONTRACT
                ),
                service.getPreferredEmploymentTypes(candidate)
        );
    }

    @Test
    void shouldIgnoreUnknownEmploymentTypes() throws Exception {

        JsonNode preferredTypes = objectMapper.readTree("""
                [
                    "FULL_TIME",
                    "SOMETHING_UNKNOWN"
                ]
                """);

        CandidateProfile candidate = new CandidateProfile(
                "Samyak",
                "samyak@example.com",
                "test-password-hash",
                "Bhopal",
                null,
                null,
                null,
                true,
                new BigDecimal("2"),
                preferredTypes
        );

        assertEquals(
                List.of(EmploymentType.FULL_TIME),
                service.getPreferredEmploymentTypes(candidate)
        );
    }

    @Test
    void shouldReturnEmptyListWhenPreferencesAreMissing() {

        CandidateProfile candidate = new CandidateProfile(
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
        );

        assertEquals(
                List.of(),
                service.getPreferredEmploymentTypes(candidate)
        );
    }
}