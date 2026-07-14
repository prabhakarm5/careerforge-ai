package com.trackai.backend.dto.coverletter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCoverLetterRequest {
    @NotBlank(message = "Cover letter content cannot be empty")
    @Size(max = 30_000, message = "Cover letter must be 30000 characters or less")
    private String content;
}
