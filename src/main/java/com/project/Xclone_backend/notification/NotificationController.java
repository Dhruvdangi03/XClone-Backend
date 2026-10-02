package com.project.Xclone_backend.notification;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.notification.NotificationDtos.NotificationResponse;
import com.project.Xclone_backend.notification.NotificationDtos.UnreadCountResponse;
import com.project.Xclone_backend.security.AuthUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public CursorPage<NotificationResponse> list(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return notificationService.list(me.id(), cursor, limit);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthUser me) {
        return notificationService.unreadCount(me.id());
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthUser me) {
        notificationService.markAllRead(me.id());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        notificationService.markRead(id, me.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        notificationService.delete(id, me.id());
    }
}
