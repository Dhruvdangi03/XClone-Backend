package com.project.Xclone_backend.notification;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Xclone_backend.block.BlockRepository;
import com.project.Xclone_backend.common.ApiException;
import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.notification.NotificationDtos.NotificationResponse;
import com.project.Xclone_backend.notification.NotificationDtos.UnreadCountResponse;
import com.project.Xclone_backend.post.Post;
import com.project.Xclone_backend.user.User;
import com.project.Xclone_backend.user.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final BlockRepository blockRepository;
    private final UserMapper userMapper;

    /** Joins the caller's transaction. Skips self-notifications and pairs with a block in either direction. */
    @Transactional
    public void notify(User recipient, User actor, NotificationType type, Post post) {
        if (recipient.getId().equals(actor.getId()) || blockRepository.existsBetween(recipient.getId(), actor.getId())) {
            return;
        }
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setActor(actor);
        n.setType(type);
        n.setPost(post);
        notificationRepository.save(n);
    }

    @Transactional
    public void removeFollow(Long actorId, Long recipientId) {
        notificationRepository.deleteFollow(actorId, recipientId);
    }

    @Transactional
    public void removeLike(Long actorId, Long postId) {
        notificationRepository.deleteLike(actorId, postId);
    }

    @Transactional
    public void removeForPost(Long postId) {
        notificationRepository.deleteByPost(postId);
    }

    @Transactional
    public void removeAllInvolving(Long userId) {
        notificationRepository.deleteAllInvolving(userId);
    }

    @Transactional(readOnly = true)
    public CursorPage<NotificationResponse> list(Long userId, Long cursor, Integer limit) {
        int n = CursorPage.clampLimit(limit);
        List<Notification> rows = notificationRepository.findPage(userId, CursorPage.cursorOrMax(cursor),
                Limit.of(n + 1));
        return CursorPage.of(rows, n, Notification::getId, page -> page.stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countUnread(userId));
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        if (notificationRepository.markRead(id, userId, Instant.now()) == 0
                && !notificationRepository.existsByIdAndRecipientId(id, userId)) {
            throw ApiException.notFound("Notification not found");
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    @Transactional
    public void delete(Long id, Long userId) {
        if (notificationRepository.delete(id, userId) == 0) {
            throw ApiException.notFound("Notification not found");
        }
    }

    private NotificationResponse toResponse(Notification n) {
        Post post = n.getPost();
        return new NotificationResponse(n.getId(), n.getType(), userMapper.toSummary(n.getActor()),
                post == null ? null : post.getId(), post == null ? null : post.getContent(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
