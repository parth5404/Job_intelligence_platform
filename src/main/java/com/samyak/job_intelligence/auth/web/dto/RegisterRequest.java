package com.samyak.job_intelligence.auth.web.dto;

import com.samyak.job_intelligence.candidate.service.CandidateProfileService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

public record RegisterRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotBlank
        String confirmPassword,

        @Size(max = 255)
        String currentLocation,

        @NotNull
        JsonNode preferredLocations,

        @PositiveOrZero
        BigDecimal minimumSalary,

        @Size(max = 10)
        String minimumSalaryCurrency,

        @NotNull
        @PositiveOrZero
        BigDecimal experienceYears,

        @NotNull
        JsonNode preferredEmploymentTypes
) {

    public RegisterRequest {
        // Normalize before bean validation so padded or upper-case emails are accepted and canonicalized.
        email = CandidateProfileService.normalizeEmail(email);
    }

    @JsonIgnore
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return password == null || confirmPassword == null || password.equals(confirmPassword);
    }

    /** BCrypt only considers the first 72 bytes, so longer (multi-byte) passwords are rejected. */
    @JsonIgnore
    @AssertTrue(message = "Password must not exceed 72 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", email=" + email + ", password=<redacted>, confirmPassword=<redacted>]";
    }
}
