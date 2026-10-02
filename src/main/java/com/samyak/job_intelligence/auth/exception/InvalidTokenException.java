package com.samyak.job_intelligence.auth.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown when a token is malformed, expired, tampered with, of the wrong type, revoked or reused.
 * The reason is never exposed to clients.
 */
public class InvalidTokenException extends AuthenticationException {

    public InvalidTokenException() {
        super("Invalid or expired token");
    }

    public InvalidTokenException(Throwable cause) {
        super("Invalid or expired token", cause);
    }
}
