package com.samyak.job_intelligence.candidate.web;

import com.samyak.job_intelligence.candidate.domain.CandidateSkill;
import com.samyak.job_intelligence.candidate.service.CandidateSkillService;
import com.samyak.job_intelligence.candidate.web.dto.CandidateSkillBulkRequest;
import com.samyak.job_intelligence.candidate.web.dto.CandidateSkillBulkResponse;
import com.samyak.job_intelligence.candidate.web.dto.CandidateSkillCreateRequest;
import com.samyak.job_intelligence.candidate.web.dto.CandidateSkillResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/candidates/{candidateProfileId}/skills")
@PreAuthorize("@candidateAccess.isSelf(#candidateProfileId)")
public class CandidateSkillController {
    private final CandidateSkillService candidateSkillService;


    public CandidateSkillController(CandidateSkillService candidateSkillService) {
        this.candidateSkillService = candidateSkillService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)

    public CandidateSkillResponse addSkill(@PathVariable Long candidateProfileId ,
                                           @Valid @RequestBody CandidateSkillCreateRequest request){
        CandidateSkill candidateSkill = candidateSkillService.addSkill(
                candidateProfileId,
                request.skill(),
                request.proficiency(),
                request.yearsExperience(),
                request.primary());
        return  CandidateSkillResponse.from(candidateSkill);
    }
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateSkillBulkResponse addSkills(
            @PathVariable Long candidateProfileId,
            @Valid @RequestBody CandidateSkillBulkRequest request
    ) {
        return candidateSkillService.addSkills(
                candidateProfileId,
                request.skills()
        );
    }
}
