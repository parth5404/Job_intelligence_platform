package com.samyak.job_intelligence.auth.service;

import com.samyak.job_intelligence.auth.domain.CandidateRefreshToken;
import com.samyak.job_intelligence.auth.exception.InvalidCredentialsException;
import com.samyak.job_intelligence.auth.exception.InvalidTokenException;
import com.samyak.job_intelligence.auth.repository.CandidateRefreshTokenRepository;
import com.samyak.job_intelligence.auth.security.IssuedToken;
import com.samyak.job_intelligence.auth.security.JwtTokenProvider;
import com.samyak.job_intelligence.auth.security.TokenClaims;
import com.samyak.job_intelligence.auth.security.TokenType;
import com.samyak.job_intelligence.auth.web.dto.AuthResponse;
import com.samyak.job_intelligence.auth.web.dto.LoginRequest;
import com.samyak.job_intelligence.auth.web.dto.RegisterRequest;
import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import com.samyak.job_intelligence.candidate.service.CandidateProfileService;
import com.samyak.job_intelligence.candidate.web.dto.CandidateResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    /** BCrypt ignores input beyond 72 bytes; longer passwords can never have been registered. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final CandidateProfileService candidateProfileService;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateRefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * Verified against when the email is unknown (or the account cannot log in) so that every
     * failed login performs one BCrypt comparison and takes comparable time.
     */
    private final String dummyPasswordHash;

    public AuthService(
            CandidateProfileService candidateProfileService,
            CandidateProfileRepository candidateProfileRepository,
            CandidateRefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider tokenProvider,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.candidateProfileService = candidateProfileService;
        this.candidateProfileRepository = candidateProfileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        CandidateProfile candidate = candidateProfileService.create(
                request.name().trim(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.currentLocation(),
                request.preferredLocations(),
                request.minimumSalary(),
                request.minimumSalaryCurrency(),
                request.experienceYears(),
                request.preferredEmploymentTypes()
        );
        return issueTokens(candidate);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = CandidateProfileService.normalizeEmail(request.email());
        CandidateProfile candidate = candidateProfileRepository.findByEmail(email).orElse(null);

        String rawPassword = request.password();
        boolean canAttempt = candidate != null
                && candidate.isActive()
                && isBcryptHash(candidate.getPasswordHash())
                && rawPassword.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;

        String hashToCheck = canAttempt ? candidate.getPasswordHash() : dummyPasswordHash;
        String passwordToCheck = canAttempt ? rawPassword : "dummy";
        boolean matches = passwordEncoder.matches(passwordToCheck, hashToCheck);

        if (!canAttempt || !matches) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(candidate);
    }

    /**
     * Rotates a refresh token. {@code noRollbackFor} is essential: when reuse of an already revoked
     * token is detected, all of the candidate's tokens are revoked and then {@link InvalidTokenException}
     * is thrown; without it the exception would roll back that revocation.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        TokenClaims claims = tokenProvider.parse(rawRefreshToken, TokenType.REFRESH);
        Instant now = clock.instant();

        CandidateRefreshToken stored = refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .orElseThrow(InvalidTokenException::new);

        if (!stored.getCandidateProfile().getId().equals(claims.candidateId())) {
            throw new InvalidTokenException();
        }

        if (stored.isRevoked()) {
            // A rotated or logged-out token is being replayed: assume theft and revoke the whole family.
            refreshTokenRepository.revokeAllActiveByCandidateProfileId(claims.candidateId(), now);
            throw new InvalidTokenException();
        }

        if (stored.isExpired(now)) {
            throw new InvalidTokenException();
        }

        CandidateProfile candidate = candidateProfileRepository.findById(claims.candidateId())
                .filter(CandidateProfile::isActive)
                .orElseThrow(InvalidTokenException::new);

        stored.revoke(now);
        return issueTokens(candidate);
    }

    /**
     * Revokes every active refresh token of the candidate. Already issued access tokens are stateless
     * and remain valid until they expire.
     */
    @Transactional
    public void logout(Long candidateProfileId) {
        refreshTokenRepository.revokeAllActiveByCandidateProfileId(candidateProfileId, clock.instant());
    }

    @Transactional(readOnly = true)
    public CandidateResponse currentCandidate(Long candidateProfileId) {
        return CandidateResponse.from(candidateProfileService.getById(candidateProfileId));
    }

    private AuthResponse issueTokens(CandidateProfile candidate) {
        IssuedToken accessToken = tokenProvider.issueAccessToken(candidate.getId(), candidate.getEmail());
        IssuedToken refreshToken = tokenProvider.issueRefreshToken(candidate.getId(), candidate.getEmail());

        refreshTokenRepository.save(new CandidateRefreshToken(
                candidate,
                TokenHasher.sha256Hex(refreshToken.value()),
                refreshToken.expiresAt()
        ));

        return new AuthResponse(
                AuthResponse.BEARER,
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.expiresAt(),
                CandidateResponse.from(candidate)
        );
    }

    private static boolean isBcryptHash(String hash) {
        return hash != null && hash.startsWith("$2");
    }
}
