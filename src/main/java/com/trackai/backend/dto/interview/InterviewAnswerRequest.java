package com.trackai.backend.dto.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewAnswerRequest {
    @NotBlank(message = "Answer cannot be empty")
    @Size(max = 10_000, message = "Answer must be 10000 characters or less")
    private String answer;
}
