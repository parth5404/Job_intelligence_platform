package com.samyak.job_intelligence.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ownership check used from {@code @PreAuthorize("@candidateAccess.isSelf(#candidateProfileId)")}.
 * Authorization is decided only from the id in the URL path and the authenticated principal,
 * never from request bodies or query parameters.
 */
@Component("candidateAccess")
public class CandidateAccessGuard {

    public boolean isSelf(Long candidateProfileId) {
        if (candidateProfileId == null) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getPrincipal() instanceof AuthenticatedCandidate candidate
                && candidateProfileId.equals(candidate.id());
    }
}
