
package com.trackai.backend.service;

import com.trackai.backend.dto.chat.ConversationDetailsResponse;
import com.trackai.backend.dto.chat.ConversationResponse;

import java.util.List;

public interface ConversationService {

        List<ConversationResponse> getRecentChats();

        List<ConversationResponse> getArchivedChats();

        List<ConversationResponse> searchChats(
                        String keyword);

        ConversationDetailsResponse getConversation(
                        String conversationId);

        void archiveConversation(
                        String conversationId);

        void restoreConversation(
                        String conversationId);

        void deleteConversation(
                        String conversationId);

        void renameConversation(
                        String conversationId,
                        String title);

        void pinConversation(
                        String conversationId);

        void unpinConversation(
                        String conversationId);
}
