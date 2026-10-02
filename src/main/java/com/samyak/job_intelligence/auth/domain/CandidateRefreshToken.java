package com.samyak.job_intelligence.auth.domain;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.common.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

/**
 * Server-side record of an issued refresh token. Only the SHA-256 digest of the token is stored.
 * This entity is internal authentication state and must never be returned from the API.
 */
@Getter
@Entity
@Table(name = "candidate_refresh_tokens", indexes = {
        @Index(name = "idx_candidate_refresh_tokens_candidate", columnList = "candidate_profile_id")
})
public class CandidateRefreshToken extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "candidate_profile_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_candidate_refresh_tokens_candidate")
    )
    private CandidateProfile candidateProfile;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected CandidateRefreshToken() {
        //required by JPA
    }

    public CandidateRefreshToken(CandidateProfile candidateProfile, String tokenHash, Instant expiresAt) {
        this.candidateProfile = candidateProfile;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
