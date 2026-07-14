package com.trackai.backend.service.impl;

import com.trackai.backend.entity.InterviewSession;
import com.trackai.backend.entity.InterviewTurn;
import com.trackai.backend.repository.InterviewSessionRepository;
import com.trackai.backend.repository.InterviewTurnRepository;
import com.trackai.backend.service.InterviewPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewPersistenceServiceImpl implements InterviewPersistenceService {
    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;

    @Override
    @Transactional
    public InterviewSession saveSession(InterviewSession session) {
        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public InterviewSession saveEvaluation(InterviewSession session, InterviewTurn turn) {
        turnRepository.save(turn);
        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void delete(InterviewSession session) {
        turnRepository.deleteBySessionId(session.getId());
        sessionRepository.delete(session);
    }
}
