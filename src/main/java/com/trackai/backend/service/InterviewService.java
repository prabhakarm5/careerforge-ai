package com.trackai.backend.service;

import com.trackai.backend.dto.interview.*;

import java.util.List;

public interface InterviewService {
    InterviewSessionResponse start(StartInterviewRequest request);
    InterviewSessionResponse answer(String sessionId, InterviewAnswerRequest request);
    InterviewSessionResponse get(String sessionId);
    List<InterviewSummaryResponse> history();
    void delete(String sessionId);
}
