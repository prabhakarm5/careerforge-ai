
package com.trackai.backend.repository;

import com.trackai.backend.entity.Conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository
                extends JpaRepository<Conversation, String> {
        List<Conversation>

                        findByUserIdAndArchivedFalseOrderByUpdatedAtDesc(

                                        String userId);

        List<Conversation>

                        findByUserIdAndArchivedTrueOrderByUpdatedAtDesc(

                                        String userId);

        List<Conversation>

                        findByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(

                                        String userId,

                                        String keyword);

}
