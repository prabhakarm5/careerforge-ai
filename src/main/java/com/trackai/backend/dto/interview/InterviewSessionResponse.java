package com.trackai.backend.dto.interview;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InterviewSessionResponse {
    private String id;
    private String resumeProjectId;
    private String role;
    private String company;
    private String jobDescription;
    private String type;
    private String difficulty;
    private String status;
    private String modelId;
    private String modelLabel;
    private Integer totalQuestions;
    private Integer currentQuestionNumber;
    private String currentQuestion;
    private String currentFocus;
    private Integer overallScore;
    private String summary;
    private boolean completed;
    private List<InterviewTurnResponse> turns;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
