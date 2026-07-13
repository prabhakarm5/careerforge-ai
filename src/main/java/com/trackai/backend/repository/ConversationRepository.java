package com.trackai.backend.repository;

import com.trackai.backend.entity.Conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository
                extends JpaRepository<Conversation, String> {

        boolean existsByIdAndUserId(String id, String userId);

        Optional<Conversation> findByIdAndUserId(String id, String userId);

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

        // ── NEW: targeted updates used by ChatServiceImpl's streaming fix ──
        //
        // Why these exist: the background title-generation thread and the
        // main streaming thread used to both call .save() on the SAME
        // in-memory Conversation object (one setting title, the other
        // setting updatedAt) from two different threads. That's a race —
        // whichever thread's save() ran last could silently overwrite the
        // other thread's field change, or in some timing windows throw.
        // These two methods do a narrow, single-column SQL UPDATE instead
        // of re-saving the whole entity, so the two concerns never touch
        // the same in-memory object or step on each other.

        @Modifying
        @Transactional
        @Query("UPDATE Conversation c SET c.title = :title WHERE c.id = :id")
        int updateTitle(@Param("id") String id, @Param("title") String title);

        @Modifying
        @Transactional
        @Query("UPDATE Conversation c SET c.updatedAt = :updatedAt WHERE c.id = :id")
        int updateTimestamp(@Param("id") String id, @Param("updatedAt") LocalDateTime updatedAt);

}