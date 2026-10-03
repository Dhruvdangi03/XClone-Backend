package com.project.Xclone_backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.project.Xclone_backend.config.CorsProperties;

import lombok.RequiredArgsConstructor;

/**
 * STOMP over a plain WebSocket at {@code /ws}. Clients send to {@code /app/...}; per-user pushes arrive on
 * {@code /user/queue/...}. Uses the in-memory broker, so it is single-instance. {@code @Valid} on STOMP payloads
 * uses the application's bean validator automatically.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final int MAX_FRAME_BYTES = 16 * 1024;

    private final CorsProperties corsProperties;
    private final StompAuthChannelInterceptor authInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(MAX_FRAME_BYTES);
    }
}
