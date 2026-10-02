package com.project.Xclone_backend.message;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Newest first; callers reverse the page for display. */
    @Query("""
            select m from Message m join fetch m.sender
            where m.conversation.id = :conversationId and m.id < :cursor
            order by m.id desc
            """)
    List<Message> findPage(Long conversationId, long cursor, Limit limit);
}
