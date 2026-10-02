package com.project.Xclone_backend.notification;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Newest first; hides actors blocked either way, inactive actors and soft-deleted posts. */
    @Query("""
            select n from Notification n join fetch n.actor left join fetch n.post p
            where n.recipient.id = :userId and n.id < :cursor
              and n.actor.status = com.project.Xclone_backend.user.AccountStatus.ACTIVE
              and (p is null or p.deleted = false)
              and not exists (select 1 from Block b
                   where (b.blocker.id = :userId and b.blocked.id = n.actor.id)
                      or (b.blocker.id = n.actor.id and b.blocked.id = :userId))
            order by n.id desc
            """)
    List<Notification> findPage(Long userId, long cursor, Limit limit);

    @Query("""
            select count(n) from Notification n left join n.post p
            where n.recipient.id = :userId and n.readAt is null
              and n.actor.status = com.project.Xclone_backend.user.AccountStatus.ACTIVE
              and (p is null or p.deleted = false)
              and not exists (select 1 from Block b
                   where (b.blocker.id = :userId and b.blocked.id = n.actor.id)
                      or (b.blocker.id = n.actor.id and b.blocked.id = :userId))
            """)
    long countUnread(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.recipient.id = :userId and n.readAt is null")
    int markRead(Long id, Long userId, Instant now);

    boolean existsByIdAndRecipientId(Long id, Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipient.id = :userId and n.readAt is null")
    void markAllRead(Long userId, Instant now);

    @Modifying
    @Query("delete from Notification n where n.id = :id and n.recipient.id = :userId")
    int delete(Long id, Long userId);

    @Modifying
    @Query("""
            delete from Notification n
            where n.type = com.project.Xclone_backend.notification.NotificationType.LIKE
              and n.actor.id = :actorId and n.post.id = :postId
            """)
    void deleteLike(Long actorId, Long postId);

    @Modifying
    @Query("""
            delete from Notification n
            where n.type = com.project.Xclone_backend.notification.NotificationType.REPOST
              and n.actor.id = :actorId and n.post.id = :postId
            """)
    void deleteRepost(Long actorId, Long postId);

    @Modifying
    @Query("""
            delete from Notification n
            where n.type = com.project.Xclone_backend.notification.NotificationType.FOLLOW
              and n.actor.id = :actorId and n.recipient.id = :recipientId
            """)
    void deleteFollow(Long actorId, Long recipientId);

    @Modifying
    @Query("delete from Notification n where n.post.id = :postId")
    void deleteByPost(Long postId);

    @Modifying
    @Query("delete from Notification n where n.actor.id = :userId or n.recipient.id = :userId")
    void deleteAllInvolving(Long userId);
}
