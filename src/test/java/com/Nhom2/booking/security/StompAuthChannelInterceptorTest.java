package com.Nhom2.booking.security;

import com.Nhom2.booking.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class StompAuthChannelInterceptorTest {

    private final JwtUtil jwtUtil = new JwtUtil();
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtUtil);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void connectWithAValidTokenAuthenticatesTheSession() {
        String token = jwtUtil.generateToken("alice", UserRole.USER);

        Message<?> result = interceptor.preSend(connect("Bearer " + token), channel);

        Principal user = StompHeaderAccessor.wrap(result).getUser();
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo("alice");
    }

    @Test
    void connectWithoutOrWithAnInvalidTokenIsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), channel)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer not-a-jwt"), channel)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void subscribingToOnesOwnQueueIsAllowed() {
        Message<?> subscribe = subscribe("/user/queue/support-chat", authenticated("alice"));

        assertThat(interceptor.preSend(subscribe, channel)).isSameAs(subscribe);
    }

    @Test
    void subscribingToSharedTopicsIsRefusedSoNobodyCanListenToOtherConversations() {
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/anything", authenticated("alice")), channel))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/queue/support-chat", authenticated("alice")), channel))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void unauthenticatedSubscribeIsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/user/queue/support-chat", null), channel))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void clientSendsAreRefusedBecauseMessagesGoThroughTheRestApi() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/anything");
        accessor.setUser(authenticated("alice"));
        accessor.setLeaveMutable(true);
        Message<byte[]> send = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(send, channel)).isInstanceOf(RuntimeException.class);
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> subscribe(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Principal authenticated(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
