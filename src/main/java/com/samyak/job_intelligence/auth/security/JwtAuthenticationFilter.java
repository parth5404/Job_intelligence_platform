package com.samyak.job_intelligence.auth.security;

import com.samyak.job_intelligence.auth.exception.InvalidTokenException;
import com.samyak.job_intelligence.candidate.domain.CandidateProfile;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <access token>}.
 *
 * Intentionally NOT a Spring bean: it is constructed by {@code SecurityConfig} and registered only
 * inside the security filter chain, which avoids Spring Boot also registering it as a servlet filter.
 *
 * A missing, malformed, expired, tampered or wrong-type token never authenticates the request. The
 * request then continues unauthenticated, so protected routes answer with the JSON 401 from
 * {@link RestAuthenticationEntryPoint} while public routes (login, refresh, ...) keep working even if
 * the client still sends a stale token. The reason a token was rejected is never exposed.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final CandidateProfileRepository candidateProfileRepository;

    public JwtAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            CandidateProfileRepository candidateProfileRepository
    ) {
        this.tokenProvider = tokenProvider;
        this.candidateProfileRepository = candidateProfileRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            authenticate(token, request).ifPresentOrElse(
                    authentication -> {
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(authentication);
                        SecurityContextHolder.setContext(context);
                    },
                    SecurityContextHolder::clearContext
            );
        }

        filterChain.doFilter(request, response);
    }

    private Optional<UsernamePasswordAuthenticationToken> authenticate(String token, HttpServletRequest request) {
        TokenClaims claims;
        try {
            claims = tokenProvider.parse(token, TokenType.ACCESS);
        } catch (InvalidTokenException ex) {
            return Optional.empty();
        }

        // The candidate must still exist, be active and still own the email the token was issued for.
        Optional<CandidateProfile> candidate = candidateProfileRepository.findById(claims.candidateId());
        if (candidate.isEmpty()
                || !candidate.get().isActive()
                || !claims.email().equals(candidate.get().getEmail())) {
            return Optional.empty();
        }

        AuthenticatedCandidate principal = new AuthenticatedCandidate(
                candidate.get().getId(),
                candidate.get().getEmail()
        );
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return Optional.of(authentication);
    }
}
