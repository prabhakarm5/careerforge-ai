package com.trackai.backend.dto.interview;

import com.trackai.backend.enums.InterviewDifficulty;
import com.trackai.backend.enums.InterviewType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class StartInterviewRequest {
    @Size(max = 64)
    private String resumeProjectId;

    @NotBlank(message = "Target role is required")
    @Size(max = 140)
    private String role;

    @Size(max = 140)
    private String company;

    @NotBlank(message = "Job description is required")
    @Size(max = 20_000)
    private String jobDescription;

    @NotNull
    private InterviewType type;

    @NotNull
    private InterviewDifficulty difficulty;

    @Min(3)
    @Max(10)
    private int questionCount = 5;

    @Size(max = 100)
    private String model;
}
