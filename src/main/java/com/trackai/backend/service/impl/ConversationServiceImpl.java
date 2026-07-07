package com.trackai.backend.service.impl;

import com.trackai.backend.dto.cache.CachedConversation;
import com.trackai.backend.dto.chat.*;
import com.trackai.backend.entity.Conversation;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.repository.ChatMessageRepository;
import com.trackai.backend.repository.ConversationRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.ConversationService;
import com.trackai.backend.service.RedisChatMemoryCacheService;
import com.trackai.backend.service.RedisConversationCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl
                implements ConversationService {

        private final ConversationRepository conversationRepository;

        private final ChatMessageRepository chatMessageRepository;

        private final UserRepository userRepository;

        // FIX (naya): ChatServiceImpl wala hi conversation metadata cache
        // (same Redis key "conversation_cache:{id}") — isliye dono service
        // ek hi cache share karte hain, consistent rehta hai. Yahan iska
        // use do jagah: (1) getConversation() mein DB findById() bachane
        // ke liye, (2) har mutation (archive/pin/rename etc) ke baad
        // write-through refresh karne ke liye taaki chat wala flow turant
        // updated title/pinned/archived dekhe, stale cache kabhi na mile.
        private final RedisConversationCacheService redisConversationCacheService;

        // FIX (naya): jab conversation hi delete ho jaaye to uska
        // chat-memory (last-20 Redis LIST) bhi saath mein evict karna
        // zaroori hai — warna orphan key Redis mein 2hr tak pada rahega
        // (TTL khud saaf kar dega, lekin turant evict karna cleaner hai
        // aur agar kisi ne ussi conversationId se dobara chat try kiya
        // to purani/stale memory serve hone se bhi bachta hai).
        private final RedisChatMemoryCacheService redisChatMemoryCacheService;

        private String conversationNOtFound = "Conversation not found";

        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder
                                .getContext()
                                .getAuthentication();

                String email = authentication.getName();

                return userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));
        }

        private ConversationResponse mapToResponse(
                        Conversation conversation) {

                return ConversationResponse.builder()
                                .id(conversation.getId())
                                .title(conversation.getTitle())
                                .featureType(conversation.getFeatureType())
                                .archived(conversation.getArchived())
                                .createdAt(conversation.getCreatedAt())
                                .updatedAt(conversation.getUpdatedAt())
                                .build();
        }

        // ===================== CACHE HELPERS =====================

        // FIX (naya): Conversation entity -> CachedConversation, turant
        // Redis mein WRITE-THROUGH save. ChatServiceImpl ka hi
        // syncConversationCache() — same shape, same key prefix, isliye
        // yahan se update hone ke turant baad chat flow bhi fresh data
        // dekhega (aur vice-versa).
        private void syncConversationCache(Conversation conversation) {

                CachedConversation cached = CachedConversation.builder()
                                .id(conversation.getId())
                                .userId(conversation.getUserId())
                                .title(conversation.getTitle())
                                .featureType(conversation.getFeatureType() != null
                                                ? conversation.getFeatureType().name()
                                                : null)
                                .archived(conversation.getArchived())
                                .pinned(conversation.getPinned())
                                .createdAt(conversation.getCreatedAt())
                                .updatedAt(conversation.getUpdatedAt())
                                .build();

                redisConversationCacheService.saveConversation(cached);
        }

        // FIX (naya): mutation methods (archive/restore/rename/pin/unpin)
        // ke liye — ye hamesha DB se hi fresh entity fetch karta hai
        // (cache se NAHI), kyunki humein turant .save() karna hai aur
        // JPA-managed entity chahiye (cache se banaya detached object
        // save karna risky hai — dusre fields silently overwrite ho
        // sakte hain). Cache sirf READ-ONLY fast-path (getConversation)
        // ke liye use hota hai, neeche dekho.
        private Conversation fetchConversationForMutation(String conversationId) {

                return conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new RuntimeException(
                                                conversationNOtFound));
        }

        @Override
        public List<ConversationResponse> getRecentChats() {

                // NOTE: List queries (recent/archived/search) jaanbujh kar
                // cache NAHI kiye — ye per-user, filtered aur sorted list
                // hai. Single-key Redis cache mein daalne ka matlab hoga
                // har chhote se mutation (naya msg aane pe updatedAt bump,
                // pin/archive) pe puri list invalidate karna — jo list
                // cache ko DB query se zyada costly bana dega. Ye endpoint
                // anyway chat ke hottest path (per-message) mein nahi hai,
                // sirf sidebar load pe chalta hai — DB yahan theek hai.
                User user = getAuthenticatedUser();

                return conversationRepository

                                .findByUserIdAndArchivedFalseOrderByUpdatedAtDesc(
                                                user.getId())

                                .stream()

                                .map(this::mapToResponse)

                                .toList();
        }

        @Override
        public List<ConversationResponse> getArchivedChats() {

                User user = getAuthenticatedUser();

                return conversationRepository

                                .findByUserIdAndArchivedTrueOrderByUpdatedAtDesc(
                                                user.getId())

                                .stream()

                                .map(this::mapToResponse)

                                .toList();
        }

        @Override
        public List<ConversationResponse> searchChats(
                        String keyword) {

                User user = getAuthenticatedUser();

                return conversationRepository

                                .findByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(

                                                user.getId(),

                                                keyword)

                                .stream()

                                .map(this::mapToResponse)

                                .toList();
        }

        @Override
        public ConversationDetailsResponse getConversation(
                        String conversationId) {

                // FIX (naya): CACHE-FIRST — title/id ke liye pehle Redis
                // check, DB findById() sirf cache-MISS pe. Messages
                // hamesha DB se hi aayenge (poori history dikhani hai
                // yahan, sirf last-20 wala "memory" list kaafi nahi hai —
                // isliye chatMessageRepository wala part waisa hi rehne
                // diya hai, koi shortcut nahi liya).
                CachedConversation cachedConversation = redisConversationCacheService
                                .getConversation(conversationId);

                String conversationIdResolved;
                String titleResolved;

                if (cachedConversation != null) {

                        conversationIdResolved = cachedConversation.getId();
                        titleResolved = cachedConversation.getTitle();

                } else {

                        Conversation conversation =

                                        conversationRepository

                                                        .findById(conversationId)

                                                        .orElseThrow(() -> new RuntimeException(
                                                                        conversationNOtFound));

                        // cache-miss tha — turant hydrate kar do taaki agli
                        // baar isi conversation ke liye cache-hit mile
                        syncConversationCache(conversation);

                        conversationIdResolved = conversation.getId();
                        titleResolved = conversation.getTitle();
                }

                List<ChatMessageResponse> messages =

                                chatMessageRepository

                                                .findByConversationIdOrderByCreatedAtAsc(
                                                                conversationId)

                                                .stream()

                                                .map(message ->

                                                ChatMessageResponse.builder()

                                                                .role(
                                                                                message.getRole())

                                                                .content(
                                                                                message.getContent())

                                                                .promptTokens(
                                                                                message.getPromptTokens())

                                                                .completionTokens(
                                                                                message.getCompletionTokens())

                                                                .totalTokens(
                                                                                message.getTotalTokens())

                                                                .createdAt(
                                                                                message.getCreatedAt())

                                                                .build())

                                                .toList();

                return ConversationDetailsResponse.builder()

                                .conversationId(
                                                conversationIdResolved)

                                .title(
                                                titleResolved)

                                .messages(
                                                messages)

                                .build();
        }

        @Override
        public void archiveConversation(
                        String conversationId) {

                Conversation conversation = fetchConversationForMutation(conversationId);

                conversation.setArchived(true);

                conversationRepository.save(
                                conversation);

                // FIX: WRITE-THROUGH — archive hote hi cache turant refresh,
                // taaki getConversation() / chat flow stale "archived=false"
                // na dekhe
                syncConversationCache(conversation);
        }

        @Override
        public void restoreConversation(
                        String conversationId) {

                Conversation conversation = fetchConversationForMutation(conversationId);

                conversation.setArchived(false);

                conversationRepository.save(
                                conversation);

                syncConversationCache(conversation);
        }

        @Override
        public void deleteConversation(
                        String conversationId) {

                chatMessageRepository.deleteAll(

                                chatMessageRepository

                                                .findByConversationIdOrderByCreatedAtAsc(
                                                                conversationId));

                conversationRepository.deleteById(
                                conversationId);

                // FIX (naya): conversation permanently delete ho gaya —
                // dono Redis footprint saaf karo. Nahi to:
                // (1) conversation_cache:{id} 1hr tak stale pada rahega
                // (koi harm nahi, but agar kisi tarah wahi ID reuse
                // hua to galat data serve ho sakta hai)
                // (2) chat_memory:{id} 2hr tak pada rahega — agar naya
                // conversation kisi tarah wahi ID le le (practically
                // nahi hota UUID ki wajah se, but hygiene ke liye
                // turant evict karna sahi hai)
                redisConversationCacheService.evictConversation(conversationId);
                redisChatMemoryCacheService.evictMemory(conversationId);
        }

        @Override
        public void renameConversation(
                        String conversationId,
                        String title) {

                Conversation conversation = fetchConversationForMutation(conversationId);

                conversation.setTitle(
                                title);

                conversationRepository.save(
                                conversation);

                // FIX: WRITE-THROUGH — naya title turant cache mein bhi,
                // warna sidebar/chat kuch der purana title dikhata rahega
                syncConversationCache(conversation);
        }

        @Override
        public void pinConversation(
                        String conversationId) {

                Conversation conversation = fetchConversationForMutation(conversationId);

                conversation.setPinned(
                                true);

                conversationRepository.save(
                                conversation);

                syncConversationCache(conversation);
        }

        @Override
        public void unpinConversation(
                        String conversationId) {

                Conversation conversation = fetchConversationForMutation(conversationId);

                conversation.setPinned(
                                false);

                conversationRepository.save(
                                conversation);

                syncConversationCache(conversation);
        }
}