package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.dto.groq.GroqMessage;
import com.trackai.backend.service.RedisChatMemoryCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisChatMemoryCacheServiceImpl implements RedisChatMemoryCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String MEMORY_PREFIX = "chat_memory:";

    /*
     * Sirf last 160 hi rakhne hain (ChatServiceImpl.MAX_MEMORY_MESSAGES
     * se match karta hai) â€” isse Redis list KABHI unbounded nahi
     * badhegi. Bill hamesha predictable rahega chahe conversation
     * mein 5 message ho ya 5000.
     */
    private static final int MAX_MEMORY = 160;

    /*
     * Inactive conversation ki memory 2 ghante mein khud saaf ho
     * jaayegi. Active chat mein har READ pe TTL refresh hoti rahegi
     * (sliding expiry) â€” isliye jab tak user active hai, memory
     * kabhi expire nahi hogi. Isse Redis pe sirf ACTIVE
     * conversations ka data rehta hai, cold data apne aap gayab.
     */
    private static final Duration TTL = Duration.ofHours(12);

    @Override
    public void appendMessage(String conversationId, GroqMessage message) {

        try {
            String key = MEMORY_PREFIX + conversationId;

            // Never turn an expired cache into a misleading one-message history.
            // The chat message is already durable in PostgreSQL. Leaving this key
            // absent makes buildConversationMemory load the complete recent history
            // from DB and hydrate Redis before the model is called.
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                log.debug("Chat memory cache is cold; DB hydration required before append: {}", key);
                return;
            }

            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(key, json);

            // List ko hamesha last MAX_MEMORY tak trim rakho â€”
            // ye O(1)-ish operation hai, list kabhi bada nahi hoga
            redisTemplate.opsForList().trim(key, -MAX_MEMORY, -1);

            redisTemplate.expire(key, TTL);

        } catch (Exception e) {
            // DB save already ho chuka hota hai (ye method DB save
            // ke baad call hoti hai) â€” silently log karo
            log.error("Redis append failed (chat memory) for {}", conversationId, e);
        }
    }

    @Override
    public List<GroqMessage> getMemory(String conversationId) {

        try {
            String key = MEMORY_PREFIX + conversationId;

            List<String> raw = redisTemplate.opsForList().range(key, 0, -1);

            if (raw == null || raw.isEmpty()) {
                log.debug("Redis Cache MISS (chat memory) : {}", key);
                return null;
            }

            log.debug("Redis Cache HIT (chat memory) : {} ({} messages)", key, raw.size());

            // Active hai, isliye TTL refresh kar do (sliding expiry)
            redisTemplate.expire(key, TTL);

            List<GroqMessage> messages = new ArrayList<>(raw.size());
            for (String json : raw) {
                messages.add(objectMapper.readValue(json, GroqMessage.class));
            }
            return messages;

        } catch (Exception e) {
            log.error("Redis read failed (chat memory) for {}", conversationId, e);
            return null;
        }
    }

    @Override
    public void hydrateMemory(String conversationId, List<GroqMessage> messages) {

        if (messages == null || messages.isEmpty()) {
            return;
        }

        try {
            String key = MEMORY_PREFIX + conversationId;

            redisTemplate.delete(key); // purani (agar koi ho) hata do

            List<String> jsonList = new ArrayList<>(messages.size());
            for (GroqMessage m : messages) {
                jsonList.add(objectMapper.writeValueAsString(m));
            }

            redisTemplate.opsForList().rightPushAll(key, jsonList);
            redisTemplate.opsForList().trim(key, -MAX_MEMORY, -1);
            redisTemplate.expire(key, TTL);

            log.debug("Chat memory hydrated from DB : {} ({} messages)", key, messages.size());

        } catch (Exception e) {
            log.error("Redis hydrate failed (chat memory) for {}", conversationId, e);
        }
    }

    @Override
    public void evictMemory(String conversationId) {
        try {
            redisTemplate.delete(MEMORY_PREFIX + conversationId);
        } catch (Exception e) {
            log.error("Redis delete failed (chat memory) for {}", conversationId, e);
        }
    }
}