package com.trackai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor

@Builder
public class ResetPasswordRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;
}