package com.trackai.backend.service;

import com.trackai.backend.dto.groq.GroqMessage;

import java.util.List;

public interface RedisChatMemoryCacheService {

    // Naya message list ke end mein push karo + last N tak trim karo
    void appendMessage(String conversationId, GroqMessage message);

    // Poori (last N) memory ek call mein nikaalo — null agar cache
    // mein kuch nahi hai (naya conversation ya TTL expire ho gaya)
    List<GroqMessage> getMemory(String conversationId);

    // Poori memory ek saath cache mein bharo (cache-miss ke baad
    // DB se load karke hydrate karne ke liye)
    void hydrateMemory(String conversationId, List<GroqMessage> messages);

    // Conversation delete hone par ya reset ke liye
    void evictMemory(String conversationId);
}