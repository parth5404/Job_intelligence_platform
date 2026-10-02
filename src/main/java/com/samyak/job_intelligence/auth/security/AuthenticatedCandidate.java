package com.samyak.job_intelligence.auth.security;

/**
 * The authenticated principal placed in the {@code SecurityContext}. Deliberately minimal:
 * it carries no credentials and is not the {@code CandidateProfile} entity.
 */
public record AuthenticatedCandidate(Long id, String email) {
}
