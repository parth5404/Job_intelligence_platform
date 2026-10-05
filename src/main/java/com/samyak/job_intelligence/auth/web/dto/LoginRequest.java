package com.samyak.job_intelligence.auth.web.dto;

import com.samyak.job_intelligence.candidate.service.CandidateProfileService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotBlank
        @Size(max = 1024)
        String password
) {

    public LoginRequest {
        // Normalize before bean validation so padded or upper-case emails are accepted and canonicalized.
        email = CandidateProfileService.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=<redacted>]";
    }
}
