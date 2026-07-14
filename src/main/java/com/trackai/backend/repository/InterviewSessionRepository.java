package com.trackai.backend.repository;

import com.trackai.backend.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, String> {
    List<InterviewSession> findTop30ByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<InterviewSession> findByIdAndUserId(String id, String userId);
}
