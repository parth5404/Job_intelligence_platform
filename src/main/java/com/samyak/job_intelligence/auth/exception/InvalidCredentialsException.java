package com.samyak.job_intelligence.auth.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown for any failed login. The message is deliberately identical for unknown email,
 * wrong password, inactive account and accounts without a usable password.
 */
public class InvalidCredentialsException extends AuthenticationException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
