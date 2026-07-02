package com.trackai.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyResetOtpResponse {

    private String message;

    // One-time token frontend must carry to /reset-password
    private String resetToken;
}