package com.samyak.job_intelligence.candidate.service;

import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.exception.DuplicateEmailException;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class CandidateProfileService {
    private final CandidateProfileRepository candidateProfileRepository;


    public CandidateProfileService(CandidateProfileRepository candidateProfileRepository) {
        this.candidateProfileRepository = candidateProfileRepository;
    }


    /**
     * Canonical form used for storing and looking up candidate emails.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Creates a candidate profile. The caller supplies an already encoded password hash;
     * raw passwords never reach this service.
     */
    @Transactional
    public CandidateProfile create(
            String name,
            String email,
            String passwordHash,
            String currentLocation,
            JsonNode preferredLocations,
            BigDecimal minimumSalary,
            String minimumSalaryCurrency,
            BigDecimal experienceYears,
            JsonNode preferredEmploymentTypes
    ){
        String normalizedEmail = normalizeEmail(email);
        if(candidateProfileRepository.existsByEmail(normalizedEmail)){
            throw new DuplicateEmailException();
        }
        CandidateProfile candidateProfile = new CandidateProfile(
                name,
                normalizedEmail,
                passwordHash,
                currentLocation,
                preferredLocations,
                minimumSalary,
                minimumSalaryCurrency,
                true,
                experienceYears,
                preferredEmploymentTypes
        );
        try {
            // Flush so a concurrent duplicate registration surfaces here and not at commit time.
            return candidateProfileRepository.saveAndFlush(candidateProfile);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueViolation(ex)) {
                throw new DuplicateEmailException();
            }
            throw ex;
        }
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT).contains("email")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public CandidateProfile getById(Long candidateProfileId){
        return candidateProfileRepository.findById(candidateProfileId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Candidate profile not found: "
                                        + candidateProfileId
                        )
                );
    }

    @Transactional
    public CandidateProfile update(
            Long candidateProfileId,
            String name,
            String email,
            String currentLocation,
            JsonNode preferredLocations,
            BigDecimal minimumSalary,
            String minimumSalaryCurrency,
            BigDecimal experienceYears,
            JsonNode preferredEmploymentTypes
    ) {
        CandidateProfile candidateProfile = getById(candidateProfileId);

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && !normalizedEmail.equals(candidateProfile.getEmail())) {
            if (candidateProfileRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateEmailException();
            }
            candidateProfile.updateEmail(normalizedEmail);
        }

        if (name != null) {
            candidateProfile.updateName(name);
        }

        if (currentLocation != null) {
            candidateProfile.updateCurrentLocation(currentLocation);
        }

        if (preferredLocations != null) {
            candidateProfile.updatePreferredLocations(preferredLocations);
        }

        if (minimumSalary != null) {
            candidateProfile.updateMinimumSalary(minimumSalary);
        }

        if (minimumSalaryCurrency != null) {
            candidateProfile.updateMinimumSalaryCurrency(minimumSalaryCurrency);
        }

        if (experienceYears != null) {
            candidateProfile.updateExperienceYears(experienceYears);
        }

        if (preferredEmploymentTypes != null) {
            candidateProfile.updatePreferredEmploymentTypes(preferredEmploymentTypes);
        }

        return candidateProfile;
    }
}
