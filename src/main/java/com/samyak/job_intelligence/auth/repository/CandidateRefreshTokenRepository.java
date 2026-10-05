package com.samyak.job_intelligence.auth.repository;

import com.samyak.job_intelligence.auth.domain.CandidateRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CandidateRefreshTokenRepository extends JpaRepository<CandidateRefreshToken, Long> {

    /**
     * Row-locks the token so that concurrent refreshes of the same token are serialized.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CandidateRefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CandidateRefreshToken t
               SET t.revokedAt = :now,
                   t.updatedAt = :now
             WHERE t.candidateProfile.id = :candidateProfileId
               AND t.revokedAt IS NULL
            """)
    int revokeAllActiveByCandidateProfileId(
            @Param("candidateProfileId") Long candidateProfileId,
            @Param("now") Instant now
    );
}
