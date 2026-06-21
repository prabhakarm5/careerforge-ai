
package com.trackai.backend.service.impl;

import com.trackai.backend.dto.chat.*;
import com.trackai.backend.entity.Conversation;
import com.trackai.backend.entity.User;
import com.trackai.backend.repository.ChatMessageRepository;
import com.trackai.backend.repository.ConversationRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.ConversationService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl
                implements ConversationService {

        private final ConversationRepository conversationRepository;

        private final ChatMessageRepository chatMessageRepository;

        private final UserRepository userRepository;

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

        @Override
        public List<ConversationResponse> getRecentChats() {

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

                Conversation conversation =

                                conversationRepository

                                                .findById(conversationId)

                                                .orElseThrow(() -> new RuntimeException(
                                                                "Conversation not found"));

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
                                                conversation.getId())

                                .title(
                                                conversation.getTitle())

                                .messages(
                                                messages)

                                .build();
        }

        @Override
        public void archiveConversation(
                        String conversationId) {

                Conversation conversation = conversationRepository
                                .findById(conversationId)

                                .orElseThrow(() -> new RuntimeException(
                                                "Conversation not found"));

                conversation.setArchived(true);

                conversationRepository.save(
                                conversation);
        }

        @Override
        public void restoreConversation(
                        String conversationId) {

                Conversation conversation = conversationRepository
                                .findById(conversationId)

                                .orElseThrow(() -> new RuntimeException(
                                                "Conversation not found"));

                conversation.setArchived(false);

                conversationRepository.save(
                                conversation);
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
        }

        @Override
        public void renameConversation(
                        String conversationId,
                        String title) {

                Conversation conversation = conversationRepository
                                .findById(
                                                conversationId)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "Conversation not found"));

                conversation.setTitle(
                                title);

                conversationRepository.save(
                                conversation);
        }

        @Override
        public void pinConversation(
                        String conversationId) {

                Conversation conversation = conversationRepository
                                .findById(
                                                conversationId)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "Conversation not found"));

                conversation.setPinned(
                                true);

                conversationRepository.save(
                                conversation);
        }

        @Override
        public void unpinConversation(
                        String conversationId) {

                Conversation conversation = conversationRepository
                                .findById(
                                                conversationId)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "Conversation not found"));

                conversation.setPinned(
                                false);

                conversationRepository.save(
                                conversation);
        }
}
