package com.samyak.job_intelligence.auth.web;

import com.samyak.job_intelligence.auth.security.AuthenticatedCandidate;
import com.samyak.job_intelligence.auth.service.AuthService;
import com.samyak.job_intelligence.auth.web.dto.AuthResponse;
import com.samyak.job_intelligence.auth.web.dto.LoginRequest;
import com.samyak.job_intelligence.auth.web.dto.RefreshTokenRequest;
import com.samyak.job_intelligence.auth.web.dto.RegisterRequest;
import com.samyak.job_intelligence.candidate.web.dto.CandidateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedCandidate principal) {
        authService.logout(principal.id());
    }

    @GetMapping("/me")
    public CandidateResponse me(@AuthenticationPrincipal AuthenticatedCandidate principal) {
        return authService.currentCandidate(principal.id());
    }
}
