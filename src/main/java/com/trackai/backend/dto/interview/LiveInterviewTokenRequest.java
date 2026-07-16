package com.trackai.backend.dto.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LiveInterviewTokenRequest {
    @Size(max = 64)
    private String resumeProjectId;
    @NotBlank @Size(max = 140)
    private String role;
    @Size(max = 140)
    private String company;
    @Size(max = 20_000)
    private String jobDescription;
    @Size(max = 30)
    private String candidateLevel = "EARLY_CAREER";
    @Size(max = 30)
    private String interviewGoal = "JOB_INTERVIEW";
    @Size(max = 20)
    private String language = "AUTO";
    @Size(max = 20)
    private String interviewerStyle = "BALANCED";
    @Size(max = 40)
    private String interviewType = "MIXED";
    @Size(max = 20)
    private String difficulty = "INTERMEDIATE";
}
