package com.samyak.job_intelligence.candidate.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("A candidate with this email already exists");
    }
}
