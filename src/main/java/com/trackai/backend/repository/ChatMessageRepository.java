package com.trackai.backend.repository;

import com.trackai.backend.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

        /*
         * ==========================================================
         * PostgreSQL + MySQL (ACTIVE)
         * ==========================================================
         * Fetch all messages in ascending order.
         */
        List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(
                        String conversationId);

        /*
         * ==========================================================
         * PostgreSQL + MySQL (ACTIVE)
         * ==========================================================
         * Fetch latest 20 messages.
         */
        List<ChatMessage> findTop20ByConversationIdOrderByCreatedAtDesc(
                        String conversationId);

        /*
         * ==========================================================
         * PostgreSQL + MySQL (ACTIVE)
         * ==========================================================
         * Fetch latest 10 messages.
         */
        List<ChatMessage> findTop10ByConversationIdOrderByCreatedAtDesc(
                        String conversationId);

        /*
         * ==========================================================
         * PostgreSQL + MySQL (ACTIVE)
         * ==========================================================
         * Fetch first 10 messages.
         */
        List<ChatMessage> findTop10ByConversationIdOrderByCreatedAtAsc(
                        String conversationId);

        /*
         * ==========================================================
         * PostgreSQL + MySQL (ACTIVE)
         * ==========================================================
         * Pageable works on every database.
         * Better than using PageRequest directly.
         */

        List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(
                        String conversationId,
                        Pageable pageable);

        /*
         * ==========================================================
         * MySQL (NOTE)
         * ==========================================================
         * No database-specific changes required.
         * Repository is fully compatible with PostgreSQL and MySQL.
         */
}