package com.project.Xclone_backend.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.project.Xclone_backend.message.MessageSentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pushes a saved message to the recipient's /user/queue/messages once it is committed, whether it was sent over REST
 * or WebSocket. A recipient with no open session simply misses the push; the message stays available over REST.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageDeliveryListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(event.recipientId()), "/queue/messages",
                    event.message());
        } catch (RuntimeException e) {
            log.warn("Could not push message {} to user {}", event.message().id(), event.recipientId(), e);
        }
    }
}
