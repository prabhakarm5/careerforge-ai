package com.trackai.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor

@Builder
public class ResendResetOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}