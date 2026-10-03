package com.project.Xclone_backend.message;

import com.project.Xclone_backend.message.MessageDtos.MessageResponse;

/** Published inside the send transaction; consumers react after it commits. */
public record MessageSentEvent(Long recipientId, MessageResponse message) {
}
