package com.trackai.backend.dto.interview;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InterviewTurnResponse {
    private String id;
    private Integer questionNumber;
    private String question;
    private String questionFocus;
    private String answer;
    private Integer score;
    private String feedback;
    private List<String> strengths;
    private List<String> improvements;
    private String idealAnswer;
    private LocalDateTime createdAt;
}
