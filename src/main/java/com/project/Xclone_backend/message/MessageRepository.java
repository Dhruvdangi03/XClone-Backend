package com.project.Xclone_backend.message;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Newest first; callers reverse the page for display. */
    @Query("""
            select m from Message m join fetch m.sender
            where m.conversation.id = :conversationId and m.id < :cursor
            order by m.id desc
            """)
    List<Message> findPage(Long conversationId, long cursor, Limit limit);

    /** Messages in the conversation that someone else sent and the user has not read yet. */
    @Query("""
            select count(m) from Message m
            where m.conversation.id = :conversationId and m.sender.id <> :userId and m.readAt is null
            """)
    long countUnread(Long conversationId, Long userId);

    /** Across the user's conversations, hiding those blocked either way or with a non-active participant. */
    @Query("""
            select count(m) from Message m join m.conversation c
            where (c.userOne.id = :userId or c.userTwo.id = :userId)
              and m.sender.id <> :userId and m.readAt is null
              and c.userOne.status = com.project.Xclone_backend.user.AccountStatus.ACTIVE
              and c.userTwo.status = com.project.Xclone_backend.user.AccountStatus.ACTIVE
              and not exists (select 1 from Block b
                   where (b.blocker.id = c.userOne.id and b.blocked.id = c.userTwo.id)
                      or (b.blocker.id = c.userTwo.id and b.blocked.id = c.userOne.id))
            """)
    long countUnreadTotal(Long userId);

    /** Never touches the user's own messages. */
    @Modifying
    @Query("""
            update Message m set m.readAt = :now
            where m.conversation.id = :conversationId and m.sender.id <> :userId and m.readAt is null
            """)
    int markRead(Long conversationId, Long userId, Instant now);
}
