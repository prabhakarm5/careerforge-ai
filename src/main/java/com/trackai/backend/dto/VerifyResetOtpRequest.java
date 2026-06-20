package com.trackai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor

@Builder
public class VerifyResetOtpRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "OTP is required")
    private String otp;
}