package com.project.Xclone_backend.websocket;

import java.util.Optional;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.project.Xclone_backend.security.AuthUser;
import com.project.Xclone_backend.security.JwtService;
import com.project.Xclone_backend.user.AccountStatus;
import com.project.Xclone_backend.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Authenticates STOMP sessions with the same JWT and ACTIVE-account rule as the REST API. Browsers cannot set headers
 * on the WebSocket handshake, so the token is read from the CONNECT frame. A rejected frame closes the connection with
 * an ERROR frame.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";
    private static final String USER_QUEUE_PREFIX = "/user/queue/";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        StompCommand command = accessor == null ? null : accessor.getCommand();
        if (command == null) {
            return message;
        }
        switch (command) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> {
                requireActiveUser(accessor);
                String destination = accessor.getDestination();
                if (destination == null || !destination.startsWith(USER_QUEUE_PREFIX)) {
                    throw new MessageDeliveryException("Subscription not allowed");
                }
            }
            case SEND -> requireActiveUser(accessor);
            default -> {
            }
        }
        return message;
    }

    private StompPrincipal authenticate(String header) {
        Optional<AuthUser> user = header != null && header.startsWith(BEARER)
                ? jwtService.parse(header.substring(BEARER.length())) : Optional.empty();
        return user.filter(u -> userRepository.existsByIdAndStatus(u.id(), AccountStatus.ACTIVE))
                .map(StompPrincipal::new)
                .orElseThrow(() -> new MessageDeliveryException("Unauthorized"));
    }

    /** Re-checked on every frame so a deactivated account loses access without waiting for the token to expire. */
    private void requireActiveUser(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof StompPrincipal principal)
                || !userRepository.existsByIdAndStatus(principal.user().id(), AccountStatus.ACTIVE)) {
            throw new MessageDeliveryException("Unauthorized");
        }
    }
}
