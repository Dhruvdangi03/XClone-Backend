package com.project.Xclone_backend.websocket;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.FieldError;

import com.project.Xclone_backend.common.ApiException;
import com.project.Xclone_backend.message.MessageDtos.MessageResponse;
import com.project.Xclone_backend.message.MessageDtos.SendMessageRequest;
import com.project.Xclone_backend.message.MessageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin STOMP adapter over {@link MessageService}. Saving and the participant/block/deactivation rules live there;
 * delivery to the recipient happens after commit in {@link MessageDeliveryListener}.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class MessageSocketController {

    private final MessageService messageService;

    /** The saved message is acknowledged to the sender on /user/queue/sent. */
    @MessageMapping("/conversations/{conversationId}/messages")
    @SendToUser("/queue/sent")
    public MessageResponse send(@DestinationVariable Long conversationId, @Valid @Payload SendMessageRequest req,
            Principal principal) {
        return messageService.send(((StompPrincipal) principal).user().id(), conversationId, req.content());
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public SocketError onApiException(ApiException e) {
        return new SocketError(e.getStatus().value(), e.getMessage());
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public SocketError onInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map((FieldError f) -> f.getField() + " " + f.getDefaultMessage())
                .findFirst().orElse("Validation failed");
        return new SocketError(HttpStatus.BAD_REQUEST.value(), detail);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public SocketError onMalformed(MessageConversionException e) {
        return new SocketError(HttpStatus.BAD_REQUEST.value(), "Malformed message");
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public SocketError onUnexpected(Exception e) {
        log.error("Unexpected error handling WebSocket message", e);
        return new SocketError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal error");
    }
}
