package com.samyak.job_intelligence.candidate.service;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.normalization.CandidateTextNormalizer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
class CandidateLocationServiceTest {

    private final CandidateTextNormalizer candidateTextNormalizer = mock(CandidateTextNormalizer.class);

    private final CandidateLocationService service =
            new CandidateLocationService(candidateTextNormalizer);

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void shouldExtractPreferredLocations() throws Exception {

        JsonNode locations = objectMapper.readTree("""
                [
                    "Bhopal",
                    "Bangalore",
                    " Pune "
                ]
                """);

        CandidateProfile candidate = new CandidateProfile(
                "Samyak",
                "samyak@example.com",
                "test-password-hash",
                "Bhopal",
                locations,
                null,
                null,
                true,
                new java.math.BigDecimal("2"),
                null
        );

        when(candidateTextNormalizer.normalizeLocations(
                List.of("Bhopal", "Bangalore", "Pune")
        )).thenReturn(
                List.of("Bhopal", "Bangalore", "Pune")
        );
        List<String> result =
                service.getPreferredLocations(candidate);

        assertEquals(
                List.of("Bhopal", "Bangalore", "Pune"),
                result
        );
    }

    @Test
    void shouldReturnEmptyListWhenPreferredLocationsAreNull() {

        CandidateProfile candidate = new CandidateProfile(
                "Samyak",
                "samyak@example.com",
                "test-password-hash",
                "Bhopal",
                null,
                null,
                null,
                true,
                new java.math.BigDecimal("2"),
                null
        );

        assertTrue(
                service.getPreferredLocations(candidate).isEmpty()
        );
    }

    @Test
    void shouldIgnoreNonTextValues() throws Exception {

        JsonNode locations = objectMapper.readTree("""
                [
                    "Bhopal",
                    123,
                    true,
                    "Bangalore"
                ]
                """);

        CandidateProfile candidate = new CandidateProfile(
                "Samyak",
                "samyak@example.com",
                "test-password-hash",
                "Bhopal",
                locations,
                null,
                null,
                true,
                new java.math.BigDecimal("2"),
                null
        );

        when(candidateTextNormalizer.normalizeLocations(
                List.of("Bhopal", "Bangalore")
        )).thenReturn(
                List.of("Bhopal", "Bangalore")
        );
        assertEquals(
                List.of("Bhopal", "Bangalore"),
                service.getPreferredLocations(candidate)
        );
    }
}