package com.samyak.job_intelligence.auth.web.dto;

import com.samyak.job_intelligence.candidate.web.dto.CandidateResponse;

import java.time.Instant;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        CandidateResponse candidate
) {

    public static final String BEARER = "Bearer";

    @Override
    public String toString() {
        return "AuthResponse[candidate=" + candidate + ", tokens=<redacted>]";
    }
}
