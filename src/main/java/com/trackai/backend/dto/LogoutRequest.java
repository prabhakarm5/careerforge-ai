package com.trackai.backend.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class LogoutRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    @NotBlank(message = "FingerPrint ID is required")
    private String Fingerprint;
}