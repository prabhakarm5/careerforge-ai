package com.trackai.backend.dto.coverletter;

import com.trackai.backend.enums.CoverLetterStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GenerateCoverLetterRequest {
    @NotBlank(message = "Select a resume before generating a cover letter")
    @Size(max = 64)
    private String resumeProjectId;

    @NotBlank(message = "Company is required")
    @Size(max = 140)
    private String company;

    @NotBlank(message = "Role is required")
    @Size(max = 140)
    private String role;

    @NotBlank(message = "Job description is required")
    @Size(max = 20_000, message = "Job description must be 20000 characters or less")
    private String jobDescription;

    @NotNull(message = "Cover letter style is required")
    private CoverLetterStyle style;

    @Size(max = 100)
    private String model;

    @Size(max = 4_000, message = "Instructions must be 4000 characters or less")
    private String instructions;
}
