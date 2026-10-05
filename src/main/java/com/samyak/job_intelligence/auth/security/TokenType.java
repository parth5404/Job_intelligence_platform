package com.samyak.job_intelligence.auth.security;

public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

    static TokenType fromClaim(Object claim) {
        for (TokenType type : values()) {
            if (type.claimValue.equals(claim)) {
                return type;
            }
        }
        return null;
    }
}
