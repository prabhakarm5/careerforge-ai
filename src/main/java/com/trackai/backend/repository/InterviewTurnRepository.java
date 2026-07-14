package com.trackai.backend.repository;

import com.trackai.backend.entity.InterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, String> {
    List<InterviewTurn> findBySessionIdOrderByQuestionNumberAsc(String sessionId);
    void deleteBySessionId(String sessionId);
}
