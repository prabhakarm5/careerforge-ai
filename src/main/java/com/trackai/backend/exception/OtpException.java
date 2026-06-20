package com.trackai.backend.exception;

// Thrown for OTP related errors - expired, invalid, not found
public class OtpException extends RuntimeException {

    public OtpException(String message) {
        super(message);
    }
}