package com.samyak.job_intelligence.auth.security;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[expiresAt=" + expiresAt + "]";
    }
}
