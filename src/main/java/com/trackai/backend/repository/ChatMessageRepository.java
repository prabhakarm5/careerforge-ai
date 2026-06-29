package com.trackai.backend.repository;

import com.trackai.backend.entity.ChatMessage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository
                extends JpaRepository<ChatMessage, String> {

        List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(
                        String conversationId);

        List<ChatMessage> findTop20ByConversationIdOrderByCreatedAtAsc(
                        String conversationId);

        List<ChatMessage> findTop10ByConversationIdOrderByCreatedAtAsc(String conversationId);

}