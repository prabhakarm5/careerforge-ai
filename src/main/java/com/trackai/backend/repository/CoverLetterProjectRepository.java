package com.trackai.backend.repository;

import com.trackai.backend.entity.CoverLetterProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoverLetterProjectRepository extends JpaRepository<CoverLetterProject, String> {
    List<CoverLetterProject> findTop30ByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<CoverLetterProject> findByIdAndUserId(String id, String userId);
}
