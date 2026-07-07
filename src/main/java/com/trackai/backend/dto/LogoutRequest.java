package com.trackai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequest {

    // ✅ FIX — refreshToken ab cookie se aayega, body se nahi
    // isliye field hata di

    @NotBlank(message = "FingerPrint ID is required")
    private String fingerprint; // ✅ FIX — capital "Fingerprint" tha, lowercase field naming convention follow
                                // karo
}