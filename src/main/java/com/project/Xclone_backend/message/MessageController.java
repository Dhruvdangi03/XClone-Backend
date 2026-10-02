package com.project.Xclone_backend.message;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.project.Xclone_backend.common.CursorPage;
import com.project.Xclone_backend.message.MessageDtos.MessageResponse;
import com.project.Xclone_backend.message.MessageDtos.SendMessageRequest;
import com.project.Xclone_backend.message.MessageDtos.UnreadCountResponse;
import com.project.Xclone_backend.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable Long conversationId, @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody SendMessageRequest req) {
        return messageService.send(me.id(), conversationId, req.content());
    }

    @GetMapping("/{conversationId}/messages")
    public CursorPage<MessageResponse> list(@PathVariable Long conversationId, @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer limit) {
        return messageService.list(me.id(), conversationId, cursor, limit);
    }

    @GetMapping("/{conversationId}/unread-count")
    public UnreadCountResponse unreadCount(@PathVariable Long conversationId, @AuthenticationPrincipal AuthUser me) {
        return messageService.unreadCount(me.id(), conversationId);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse totalUnreadCount(@AuthenticationPrincipal AuthUser me) {
        return messageService.totalUnreadCount(me.id());
    }

    @PostMapping("/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long conversationId, @AuthenticationPrincipal AuthUser me) {
        messageService.markRead(me.id(), conversationId);
    }
}
