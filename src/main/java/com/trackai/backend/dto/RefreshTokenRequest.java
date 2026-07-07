package com.trackai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenRequest {

    // ✅ FIX — refreshToken ab cookie se aayega, body se nahi — required nahi rakha
    private String refreshToken;

    @NotBlank(message = "Fingerprint is required")
    private String fingerprint;
}