package com.project.Xclone_backend.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Idempotent and race-safe: returns 1 if the conversation was created, 0 if it already existed. */
    @Modifying
    @Query(value = """
            insert into conversations (user_one_id, user_two_id, created_at, updated_at)
            values (:userOneId, :userTwoId, now(), now())
            on conflict (user_one_id, user_two_id) do nothing
            """, nativeQuery = true)
    int createIfAbsent(Long userOneId, Long userTwoId);

    @Query("""
            select c from Conversation c join fetch c.userOne join fetch c.userTwo
            where c.userOne.id = :userOneId and c.userTwo.id = :userTwoId
            """)
    Optional<Conversation> findByPair(Long userOneId, Long userTwoId);

    @Query("""
            select c from Conversation c join fetch c.userOne join fetch c.userTwo
            where c.id = :id and (c.userOne.id = :userId or c.userTwo.id = :userId)
            """)
    Optional<Conversation> findByIdAndParticipant(Long id, Long userId);

    /** Newest first; hides pairs blocked either way and conversations with a non-active participant. */
    @Query("""
            select c from Conversation c join fetch c.userOne join fetch c.userTwo
            where (c.userOne.id = :userId or c.userTwo.id = :userId) and c.id < :cursor
              and c.userOne.status = com.project.Xclone_backend.user.AccountStatus.ACTIVE
              and c.userTwo.status = com.project.Xclone_backend.user.AccountStatus.ACTIVE
              and not exists (select 1 from Block b
                   where (b.blocker.id = c.userOne.id and b.blocked.id = c.userTwo.id)
                      or (b.blocker.id = c.userTwo.id and b.blocked.id = c.userOne.id))
            order by c.id desc
            """)
    List<Conversation> findPage(Long userId, long cursor, Limit limit);

    /** Bumps updatedAt explicitly, since a new message does not dirty the conversation entity. */
    @Modifying
    @Query("update Conversation c set c.updatedAt = :now where c.id = :id")
    int touch(Long id, Instant now);
}
