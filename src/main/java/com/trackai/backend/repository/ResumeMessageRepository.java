package com.trackai.backend.repository;

import com.trackai.backend.entity.ResumeMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeMessageRepository extends JpaRepository<ResumeMessage, String> {

    List<ResumeMessage> findByProjectIdOrderByCreatedAtDesc(String projectId, Pageable pageable);

    void deleteByProjectId(String projectId);
}
