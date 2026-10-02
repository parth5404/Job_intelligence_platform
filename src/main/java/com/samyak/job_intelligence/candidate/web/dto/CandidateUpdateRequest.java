package com.samyak.job_intelligence.candidate.web.dto;

import com.samyak.job_intelligence.candidate.service.CandidateProfileService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

public record CandidateUpdateRequest(
        @Size(max = 255)
        String name,

        @Email
        @Size(max =320)
        String email,

        @Size(max = 255)
        String currentLocation,

        JsonNode preferredLocations,

        @PositiveOrZero
        BigDecimal minimumSalary,

        @Size(max = 10)
        String minimumSalaryCurrency,

        @PositiveOrZero
        BigDecimal experienceYears,

        JsonNode preferredEmploymentTypes
) {

    public CandidateUpdateRequest {
        // Normalize before bean validation so padded or upper-case emails are accepted and canonicalized.
        email = CandidateProfileService.normalizeEmail(email);
    }
}
