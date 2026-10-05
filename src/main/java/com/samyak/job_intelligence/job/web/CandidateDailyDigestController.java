package com.samyak.job_intelligence.job.web;


import com.samyak.job_intelligence.job.service.digest.DailyDigest;
import com.samyak.job_intelligence.job.service.digest.DailyDigestService;
import com.samyak.job_intelligence.job.web.dto.DailyDigestResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/candidates")
@Validated
public class CandidateDailyDigestController {
    private final DailyDigestService dailyDigestService;


    public CandidateDailyDigestController(DailyDigestService dailyDigestService) {
        this.dailyDigestService = dailyDigestService;
    }

    @GetMapping("/{candidateProfileId}/daily-digest")
    @PreAuthorize("@candidateAccess.isSelf(#candidateProfileId)")
    public DailyDigestResponse getDailyDigest(@PathVariable Long candidateProfileId,
    @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ){
        DailyDigest digest = dailyDigestService.generate(candidateProfileId,limit);
        return DailyDigestResponse.from(digest);
    }
}
