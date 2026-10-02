package com.samyak.job_intelligence.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform JSON error body. Never contains stack traces, exception class names or internal details.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ApiErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, null);
    }

    public static ApiErrorResponse of(HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }
}
