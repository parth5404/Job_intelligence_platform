package com.samyak.job_intelligence.candidate.web;


import com.samyak.job_intelligence.auth.security.AuthenticatedCandidate;
import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.service.CandidateProfileService;
import com.samyak.job_intelligence.candidate.web.dto.CandidateResponse;
import com.samyak.job_intelligence.candidate.web.dto.CandidateUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Candidate profiles are created through {@code POST /api/v1/auth/register}.
 */
@RestController
@RequestMapping("/api/v1/candidates")
public class CandidateController {
    private final CandidateProfileService candidateProfileService;

    public CandidateController(CandidateProfileService candidateProfileService) {
        this.candidateProfileService = candidateProfileService;
    }

    @GetMapping("/me")
    public CandidateResponse getMe(@AuthenticationPrincipal AuthenticatedCandidate principal) {
        return CandidateResponse.from(candidateProfileService.getById(principal.id()));
    }

    @PatchMapping("/me")
    public CandidateResponse updateMe(
            @AuthenticationPrincipal AuthenticatedCandidate principal,
            @Valid @RequestBody CandidateUpdateRequest request
    ) {
        return update(principal.id(), request);
    }

    @PatchMapping("/{candidateProfileId}")
    @PreAuthorize("@candidateAccess.isSelf(#candidateProfileId)")
    public CandidateResponse update(
            @PathVariable Long candidateProfileId,
            @Valid @RequestBody CandidateUpdateRequest request
    ){
        CandidateProfile candidateProfile =
                candidateProfileService.update(
                        candidateProfileId,
                        request.name(),
                        request.email(),
                        request.currentLocation(),
                        request.preferredLocations(),
                        request.minimumSalary(),
                        request.minimumSalaryCurrency(),
                        request.experienceYears(),
                        request.preferredEmploymentTypes()
                );

        return CandidateResponse.from(candidateProfile);
    }

}
