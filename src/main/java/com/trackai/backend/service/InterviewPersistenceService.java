package com.trackai.backend.service;

import com.trackai.backend.entity.InterviewSession;
import com.trackai.backend.entity.InterviewTurn;

public interface InterviewPersistenceService {
    InterviewSession saveSession(InterviewSession session);
    InterviewSession saveEvaluation(InterviewSession session, InterviewTurn turn);
    void delete(InterviewSession session);
}
