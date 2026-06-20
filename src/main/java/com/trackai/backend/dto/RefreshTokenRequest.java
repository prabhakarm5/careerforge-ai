package com.trackai.backend.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.*;

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor

@Builder
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    @NotBlank(message = "Fingerprint is required")
    private String fingerprint;
}