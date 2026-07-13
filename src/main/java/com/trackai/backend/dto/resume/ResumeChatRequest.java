package com.trackai.backend.dto.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeChatRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 20000, message = "Message must be 20000 characters or less")
    private String message;

    @Size(max = 100, message = "Model ID must be 100 characters or less")
    private String model;
}
