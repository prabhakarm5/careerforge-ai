package com.trackai.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.dto.groq.GroqMessage;
import com.trackai.backend.service.impl.RedisChatMemoryCacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisChatMemoryCacheServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ListOperations<String, String> listOperations;
    private RedisChatMemoryCacheServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        service = new RedisChatMemoryCacheServiceImpl(redisTemplate, new ObjectMapper());
    }

    @Test
    void expiredCacheDoesNotBecomePartialOneMessageMemory() {
        when(redisTemplate.hasKey("chat_memory:conversation-1")).thenReturn(false);

        service.appendMessage("conversation-1", new GroqMessage("user", "continue yesterday's work"));

        verify(listOperations, never()).rightPush(anyString(), anyString());
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void hydratedCacheStillAppendsAndRefreshesTtl() {
        when(redisTemplate.hasKey("chat_memory:conversation-1")).thenReturn(true);

        service.appendMessage("conversation-1", new GroqMessage("assistant", "Earlier context"));

        verify(listOperations).rightPush(anyString(), anyString());
        verify(listOperations).trim("chat_memory:conversation-1", -160, -1);
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }
}