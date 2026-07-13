package com.trackai.backend.dto.resume;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GenerateResumeRequest {

    @Size(max = 20000, message = "Instructions must be 20000 characters or less")
    private String instructions;

    @Size(max = 100, message = "Model ID must be 100 characters or less")
    private String model;
}
