package com.trackai.backend.repository;

import com.trackai.backend.entity.GeneratedImage;
import com.trackai.backend.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeneratedImageRepository
                extends JpaRepository<GeneratedImage, String> {

        /*
         * ==========================================================
         * PostgreSQL ✅
         * MySQL ✅
         *
         * Ye repository dono databases me same tarah kaam karegi.
         * Kisi bhi method ko change karne ki zarurat nahi hai.
         * ==========================================================
         */

        List<GeneratedImage> findByUserOrderByCreatedAtDesc(User user);

        Optional<GeneratedImage> findByIdAndUser(
                        String id,
                        User user);

}