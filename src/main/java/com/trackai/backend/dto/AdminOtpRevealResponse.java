package com.trackai.backend.dto;

public record AdminOtpRevealResponse(String otp, long expiresInSeconds) {
}