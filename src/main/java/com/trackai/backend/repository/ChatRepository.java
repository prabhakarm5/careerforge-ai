package com.trackai.backend.repository;

import com.trackai.backend.entity.ChatHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRepository
                extends JpaRepository<ChatHistory, String> {

        List<ChatHistory> findByUserIdOrderByCreatedAtDesc(
                        String userId);

}