package com.trackai.backend.repository;

import com.trackai.backend.entity.GeneratedImage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeneratedImageRepository extends JpaRepository<GeneratedImage, String> {
    List<GeneratedImage> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Optional<GeneratedImage> findByIdAndUserId(String id, String userId);
}