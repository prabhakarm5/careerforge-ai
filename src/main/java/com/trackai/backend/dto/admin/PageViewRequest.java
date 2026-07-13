package com.trackai.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PageViewRequest(
        @NotBlank @Size(max = 120) String path,
        @Size(max = 80) String timezone,
        @Size(max = 40) String locale) {
}