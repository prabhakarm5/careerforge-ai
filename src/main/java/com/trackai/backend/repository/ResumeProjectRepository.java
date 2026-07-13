package com.trackai.backend.repository;

import com.trackai.backend.entity.ResumeProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeProjectRepository extends JpaRepository<ResumeProject, String> {

    List<ResumeProject> findTop20ByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<ResumeProject> findByIdAndUserId(String id, String userId);
}
