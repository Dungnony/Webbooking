package com.Nhom2.booking.security;

import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The WebSocket handshake is public, so authentication happens on the STOMP CONNECT frame (same JWT as the REST
 * API). Clients may only subscribe to their own /user/queue/... destinations and never send: chat messages are
 * posted through the REST API, which applies the normal access rules.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String OWN_QUEUE_PREFIX = "/user/queue/";

    private final JwtUtil jwtUtil;

    public StompAuthChannelInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> checkSubscription(accessor);
            case SEND -> throw new MessageDeliveryException("Messages are sent through the REST API");
            default -> {
                // DISCONNECT, heartbeats etc. need no checks
            }
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        try {
            return new UsernamePasswordAuthenticationToken(
                    jwtUtil.extractUsername(token),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + jwtUtil.extractRole(token))));
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
    }

    private static void checkSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(OWN_QUEUE_PREFIX)) {
            throw new MessageDeliveryException("Only personal queues can be subscribed to");
        }
    }
}
