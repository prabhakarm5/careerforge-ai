package com.trackai.backend.service;

import com.trackai.backend.dto.cache.CachedConversation;

public interface RedisConversationCacheService {

    // Conversation ko cache mein save/overwrite karo (WRITE-THROUGH)
    void saveConversation(CachedConversation conversation);

    // Cache se conversation nikalo (null agar cache MISS)
    CachedConversation getConversation(String conversationId);

    // Cache hatao (delete/archive hone par)
    void evictConversation(String conversationId);
}