package com.samyak.job_intelligence.common.exception;

import com.samyak.job_intelligence.candidate.exception.CandidateSkillAlreadyExistsException;
import com.samyak.job_intelligence.candidate.exception.DuplicateEmailException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps exceptions to uniform {@link ApiErrorResponse} bodies. Messages for 401/403 are generic and
 * exception details are never echoed. There is intentionally no catch-all {@code Exception} handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach((FieldError error) ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request", request, null);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
            DuplicateEmailException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, "A candidate with this email already exists", request, null);
    }

    @ExceptionHandler(CandidateSkillAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleSkillAlreadyExists(
            CandidateSkillAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    /**
     * Covers InvalidCredentialsException, InvalidTokenException and any other authentication failure.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return unauthorized(request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return unauthorized(request);
        }
        return build(HttpStatus.FORBIDDEN, "Access denied", request, null);
    }

    private ResponseEntity<ApiErrorResponse> unauthorized(HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Bearer")
                .body(ApiErrorResponse.of(HttpStatus.UNAUTHORIZED, "Authentication failed", request.getRequestURI()));
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status, message, request.getRequestURI(), fieldErrors));
    }
}
