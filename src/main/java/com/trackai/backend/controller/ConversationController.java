package com.trackai.backend.controller;

import com.trackai.backend.dto.chat.ConversationDetailsResponse;
import com.trackai.backend.dto.chat.ConversationResponse;
import com.trackai.backend.dto.chat.RenameConversationRequest;
import com.trackai.backend.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

        private final ConversationService conversationService;

        // Recent Chats
        @GetMapping
        public ResponseEntity<List<ConversationResponse>> getRecentChats() {

                return ResponseEntity.ok(

                                conversationService
                                                .getRecentChats());
        }

        // Archived Chats
        @GetMapping("/archived")
        public ResponseEntity<List<ConversationResponse>> getArchivedChats() {

                return ResponseEntity.ok(

                                conversationService
                                                .getArchivedChats());
        }

        // Search Chats
        @GetMapping("/search")
        public ResponseEntity<List<ConversationResponse>> searchChats(

                        @RequestParam String keyword) {

                return ResponseEntity.ok(

                                conversationService
                                                .searchChats(
                                                                keyword));
        }

        // Lightweight active-chat check used for cross-device deletion sync.
        @GetMapping("/{conversationId}/status")
        public ResponseEntity<Map<String, Boolean>> getConversationStatus(
                        @PathVariable String conversationId) {
                return ResponseEntity.ok(Map.of(
                                "exists",
                                conversationService.conversationExists(conversationId)));
        }
        // Load Complete Conversation
        @GetMapping("/{conversationId}")
        public ResponseEntity<ConversationDetailsResponse> getConversation(

                        @PathVariable String conversationId) {

                return ResponseEntity.ok(

                                conversationService
                                                .getConversation(
                                                                conversationId));
        }

        // Archive Chat
        @PutMapping("/{conversationId}/archive")
        public ResponseEntity<String> archiveConversation(

                        @PathVariable String conversationId) {

                conversationService
                                .archiveConversation(
                                                conversationId);

                return ResponseEntity.ok(
                                "Conversation archived");
        }

        // Restore Chat
        @PutMapping("/{conversationId}/restore")
        public ResponseEntity<String> restoreConversation(

                        @PathVariable String conversationId) {

                conversationService
                                .restoreConversation(
                                                conversationId);

                return ResponseEntity.ok(
                                "Conversation restored");
        }

        // Delete Chat
        @DeleteMapping("/{conversationId}")
        public ResponseEntity<String> deleteConversation(

                        @PathVariable String conversationId) {

                conversationService
                                .deleteConversation(
                                                conversationId);

                return ResponseEntity.ok(
                                "Conversation deleted");
        }

        @PutMapping("/{id}/rename")
        public ResponseEntity<String> rename(

                        @PathVariable String id,

                        @RequestBody RenameConversationRequest request) {

                conversationService.renameConversation(

                                id,

                                request.getTitle());

                return ResponseEntity.ok(
                                "Renamed");
        }

}
