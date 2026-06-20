package com.trackai.backend.exception;

// Thrown when a user/admin account is blocked
public class AccountBlockedException extends RuntimeException {

    public AccountBlockedException(String message) {
        super(message);
    }
}