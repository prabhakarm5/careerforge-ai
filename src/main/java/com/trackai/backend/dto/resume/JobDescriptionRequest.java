package com.trackai.backend.dto.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobDescriptionRequest {

    @NotBlank(message = "Job description is required")
    @Size(max = 20000, message = "Job description must be 20000 characters or less")
    private String jobDescription;

    @Size(max = 100, message = "Model ID must be 100 characters or less")
    private String model;
}
