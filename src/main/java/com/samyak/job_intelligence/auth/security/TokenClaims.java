package com.samyak.job_intelligence.auth.security;

import java.time.Instant;

/**
 * Validated claims of a parsed token.
 */
public record TokenClaims(Long candidateId, String email, TokenType type, String tokenId, Instant expiresAt) {
}
