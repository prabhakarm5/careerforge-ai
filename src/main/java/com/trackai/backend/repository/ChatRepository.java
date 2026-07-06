package com.trackai.backend.repository;

import com.trackai.backend.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRepository extends JpaRepository<ChatHistory, String> {

        /*
         * ==========================================================
         * PostgreSQL + MySQL (ACTIVE)
         * ==========================================================
         * Fetch user's chat history ordered by latest first.
         *
         * Spring Data JPA automatically generates SQL.
         * No database-specific query is used.
         */
        List<ChatHistory> findByUserIdOrderByCreatedAtDesc(
                        String userId);

        /*
         * ==========================================================
         * MySQL (NOTE)
         * ==========================================================
         * No database-specific changes required.
         * Repository is fully compatible with PostgreSQL and MySQL.
         */
}