package com.project.Xclone_backend.message;

import java.time.Instant;

import com.project.Xclone_backend.user.UserDtos.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MessageDtos {

    private MessageDtos() {
    }

    public record SendMessageRequest(@NotBlank @Size(max = Message.MAX_LENGTH) String content) {
    }

    public record MessageResponse(Long id, Long conversationId, UserSummary sender, String content,
            Instant createdAt) {
    }

    public record UnreadCountResponse(long count) {
    }
}
