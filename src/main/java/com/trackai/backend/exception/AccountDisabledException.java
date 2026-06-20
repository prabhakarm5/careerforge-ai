package com.trackai.backend.exception;

// Thrown when a user/admin account is disabled or email not verified
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException(String message) {
        super(message);
    }
}