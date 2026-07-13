package com.trackai.backend.service.impl;

import com.trackai.backend.dto.cache.CachedConversation;
import com.trackai.backend.security.JwtUserPrincipal;
import com.trackai.backend.dto.chat.*;
import com.trackai.backend.dto.cache.CachedUser;
import com.trackai.backend.entity.Conversation;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.repository.ChatMessageRepository;
import com.trackai.backend.repository.ConversationRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.ConversationService;
import com.trackai.backend.service.RedisChatMemoryCacheService;
import com.trackai.backend.service.RedisConversationCacheService;
import com.trackai.backend.service.RedisUserCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
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

        private final RedisUserCacheService redisUserCacheService;

        // FIX (naya): ChatServiceImpl wala hi conversation metadata cache
        // (same Redis key "conversation_cache:{id}") â€” isliye dono service
        // ek hi cache share karte hain, consistent rehta hai. Yahan iska
        // use do jagah: (1) getConversation() mein DB findById() bachane
        // ke liye, (2) har mutation (archive/pin/rename etc) ke baad
        // write-through refresh karne ke liye taaki chat wala flow turant
        // updated title/pinned/archived dekhe, stale cache kabhi na mile.
        private final RedisConversationCacheService redisConversationCacheService;

        // FIX (naya): jab conversation hi delete ho jaaye to uska
        // chat-memory (last-20 Redis LIST) bhi saath mein evict karna
        // zaroori hai â€” warna orphan key Redis mein 2hr tak pada rahega
        // (TTL khud saaf kar dega, lekin turant evict karna cleaner hai
        // aur agar kisi ne ussi conversationId se dobara chat try kiya
        // to purani/stale memory serve hone se bhi bachta hai).
        private final RedisChatMemoryCacheService redisChatMemoryCacheService;

        private String conversationNOtFound = "Conversation not found";

        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder
                                .getContext()
                                .getAuthentication();

                if (authentication == null
                                || authentication instanceof AnonymousAuthenticationToken
                                || !authentication.isAuthenticated()) {
                        throw new RuntimeException("Please login again to load conversations");
                }

                String email = authentication.getName();
                if (authentication.getPrincipal() instanceof JwtUserPrincipal principal
                                && principal.userId() != null && !principal.userId().isBlank()) {
                        return User.builder()
                                        .id(principal.userId())
                                        .email(principal.email())
                                        .role(com.trackai.backend.enums.Role.valueOf(principal.role()))
                                        .build();
                }

                if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
                        throw new RuntimeException("Please login again to load conversations");
                }

                email = email.trim().toLowerCase();

                CachedUser cachedUser = redisUserCacheService.getUser(email);
                if (cachedUser != null) {
                        return User.builder()
                                        .id(cachedUser.getId())
                                        .name(cachedUser.getName())
                                        .email(cachedUser.getEmail())
                                        .role(cachedUser.getRole())
                                        .enabled(cachedUser.getEnabled())
                                        .blocked(cachedUser.getBlocked())
                                        .emailVerified(cachedUser.getEmailVerified())
                                        .mobileNumber(cachedUser.getMobileNumber())
                                        .profileImage(cachedUser.getProfileImage())
                                        .profileImagePublicId(cachedUser.getProfileImagePublicId())
                                        .description(cachedUser.getDescription())
                                        .createdAt(cachedUser.getCreatedAt())
                                        .build();
                }

                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "Your session is valid but the user record was not found. Please login again."));

                if (user.getRole() != com.trackai.backend.enums.Role.ROLE_ADMIN) {
                        CachedUser cache = CachedUser.builder()
                                        .id(user.getId())
                                        .name(user.getName())
                                        .email(user.getEmail())
                                        .role(user.getRole())
                                        .enabled(user.getEnabled())
                                        .blocked(user.getBlocked())
                                        .emailVerified(user.getEmailVerified())
                                        .mobileNumber(user.getMobileNumber())
                                        .profileImage(user.getProfileImage())
                                        .profileImagePublicId(user.getProfileImagePublicId())
                                        .description(user.getDescription())
                                        .createdAt(user.getCreatedAt())
                                        .build();

                        redisUserCacheService.saveUser(cache);
                }

                return user;
        }

        @Override
        public boolean conversationExists(String conversationId) {
                if (conversationId == null || conversationId.isBlank()) {
                        return false;
                }
                User user = getAuthenticatedUser();
                return conversationRepository.existsByIdAndUserId(conversationId, user.getId());
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
        // syncConversationCache() â€” same shape, same key prefix, isliye
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
        // ke liye â€” ye hamesha DB se hi fresh entity fetch karta hai
        // (cache se NAHI), kyunki humein turant .save() karna hai aur
        // JPA-managed entity chahiye (cache se banaya detached object
        // save karna risky hai â€” dusre fields silently overwrite ho
        // sakte hain). Cache sirf READ-ONLY fast-path (getConversation)
        // ke liye use hota hai, neeche dekho.
        private Conversation fetchConversationForMutation(String conversationId) {

                User user = getAuthenticatedUser();
                return conversationRepository.findByIdAndUserId(conversationId, user.getId())
                                .orElseThrow(() -> new RuntimeException(
                                                conversationNOtFound));
        }

        @Override
        public List<ConversationResponse> getRecentChats() {

                // NOTE: List queries (recent/archived/search) jaanbujh kar
                // cache NAHI kiye â€” ye per-user, filtered aur sorted list
                // hai. Single-key Redis cache mein daalne ka matlab hoga
                // har chhote se mutation (naya msg aane pe updatedAt bump,
                // pin/archive) pe puri list invalidate karna â€” jo list
                // cache ko DB query se zyada costly bana dega. Ye endpoint
                // anyway chat ke hottest path (per-message) mein nahi hai,
                // sirf sidebar load pe chalta hai â€” DB yahan theek hai.
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

                User user = getAuthenticatedUser();

                // FIX (naya): CACHE-FIRST â€” title/id ke liye pehle Redis
                // check, DB findById() sirf cache-MISS pe. Messages
                // hamesha DB se hi aayenge (poori history dikhani hai
                // yahan, sirf last-20 wala "memory" list kaafi nahi hai â€”
                // isliye chatMessageRepository wala part waisa hi rehne
                // diya hai, koi shortcut nahi liya).
                CachedConversation cachedConversation = redisConversationCacheService
                                .getConversation(conversationId);

                String conversationIdResolved;
                String titleResolved;

                if (cachedConversation != null) {
                        if (!user.getId().equals(cachedConversation.getUserId())) {
                                throw new RuntimeException(conversationNOtFound);
                        }

                        conversationIdResolved = cachedConversation.getId();
                        titleResolved = cachedConversation.getTitle();

                } else {

                        Conversation conversation =

                                        conversationRepository

                                                        .findByIdAndUserId(conversationId, user.getId())

                                                        .orElseThrow(() -> new RuntimeException(
                                                                        conversationNOtFound));

                        // cache-miss tha â€” turant hydrate kar do taaki agli
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

                // FIX: WRITE-THROUGH â€” archive hote hi cache turant refresh,
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

                fetchConversationForMutation(conversationId);

                chatMessageRepository.deleteAll(

                                chatMessageRepository

                                                .findByConversationIdOrderByCreatedAtAsc(
                                                                conversationId));

                conversationRepository.deleteById(
                                conversationId);

                // FIX (naya): conversation permanently delete ho gaya â€”
                // dono Redis footprint saaf karo. Nahi to:
                // (1) conversation_cache:{id} 1hr tak stale pada rahega
                // (koi harm nahi, but agar kisi tarah wahi ID reuse
                // hua to galat data serve ho sakta hai)
                // (2) chat_memory:{id} 2hr tak pada rahega â€” agar naya
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

                // FIX: WRITE-THROUGH â€” naya title turant cache mein bhi,
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