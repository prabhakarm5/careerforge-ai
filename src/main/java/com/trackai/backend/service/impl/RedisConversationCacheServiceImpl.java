package com.trackai.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.dto.cache.CachedConversation;
import com.trackai.backend.service.RedisConversationCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisConversationCacheServiceImpl
                implements RedisConversationCacheService {

        private final StringRedisTemplate redisTemplate;

        private final ObjectMapper objectMapper;

        private static final String CONVERSATION_PREFIX = "conversation_cache:";

        /*
         * 1 hour TTL — conversation metadata (title/timestamps) rarely
         * hit hone ke bahar bhi thodi der cache mein rehni chahiye
         * (user baar baar conversation switch karta hai sidebar mein).
         * Write-through hone ki wajah se ye stale nahi hogi jab tak
         * write is hi service (ChatServiceImpl) se ho raha hai.
         */
        private static final Duration CACHE_TTL = Duration.ofHours(1);

        @Override
        public void saveConversation(CachedConversation conversation) {

                if (conversation == null || conversation.getId() == null) {
                        return;
                }

                try {

                        String key = CONVERSATION_PREFIX + conversation.getId();

                        String json = objectMapper.writeValueAsString(conversation);

                        redisTemplate.opsForValue().set(
                                        key,
                                        json,
                                        CACHE_TTL);

                        log.info("Conversation cached (write-through) : {}", key);

                } catch (JsonProcessingException e) {

                        log.error("Failed to serialize conversation cache", e);

                } catch (Exception e) {

                        // DB write already ho chuka hota hai (ye method DB save
                        // ke baad call hoti hai) — isliye silently log karo,
                        // user-flow break nahi hona chahiye
                        log.error("Redis save failed (conversation) — DB write already succeeded, continuing", e);
                }
        }

        @Override
        public CachedConversation getConversation(String conversationId) {

                try {

                        String key = CONVERSATION_PREFIX + conversationId;

                        String json = redisTemplate.opsForValue().get(key);

                        if (json == null) {

                                log.info("Redis Cache MISS : {}", key);

                                return null;
                        }

                        log.info("Redis Cache HIT : {}", key);

                        return objectMapper.readValue(
                                        json,
                                        CachedConversation.class);

                } catch (Exception e) {

                        log.error("Redis read failed (conversation)", e);

                        return null;
                }
        }

        @Override
        public void evictConversation(String conversationId) {

                try {

                        String key = CONVERSATION_PREFIX + conversationId;

                        redisTemplate.delete(key);

                        log.info("Conversation cache evicted : {}", key);

                } catch (Exception e) {

                        log.error("Redis delete failed (conversation)", e);
                }
        }
}