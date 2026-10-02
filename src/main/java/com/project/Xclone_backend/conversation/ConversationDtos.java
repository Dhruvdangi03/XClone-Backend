package com.project.Xclone_backend.conversation;

import java.time.Instant;

import com.project.Xclone_backend.user.UserDtos.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class ConversationDtos {

    private ConversationDtos() {
    }

    public record CreateConversationRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,15}$",
                    message = "must be 3-15 characters: letters, digits or underscore") String username) {
    }

    /** {@code participant} is the other user in the conversation, never the caller. */
    public record ConversationResponse(Long id, UserSummary participant, Instant createdAt, Instant updatedAt) {
    }
}
