package com.trackai.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMessageRequest(
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 4000) String message) {
}