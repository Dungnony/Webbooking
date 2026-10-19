package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.ChatMessageResponse;
import com.Nhom2.booking.dto.ChatRoomSummary;
import com.Nhom2.booking.entity.ChatMessage;
import com.Nhom2.booking.entity.ChatRoom;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.ChatMessageRepository;
import com.Nhom2.booking.repository.ChatRoomRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportChatServiceTest {

    private ChatRoomRepository roomRepository;
    private ChatMessageRepository messageRepository;
    private SimpMessagingTemplate messaging;
    private SupportChatService chatService;
    private User alice;
    private User admin;

    @BeforeEach
    void setUp() {
        roomRepository = mock(ChatRoomRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        messaging = mock(SimpMessagingTemplate.class);
        UserRepository userRepository = mock(UserRepository.class);
        chatService = new SupportChatService(roomRepository, messageRepository, userRepository, messaging,
                Clock.fixed(Instant.parse("2026-05-01T10:00:00Z"), ZoneOffset.UTC));

        alice = user(1L, "alice", UserRole.USER);
        admin = user(9L, "admin", UserRole.ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of(admin));
        when(roomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            if (room.getId() == null) {
                room.setId(100L);
            }
            return room;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(500L);
            return message;
        });
    }

    @Test
    void firstUserMessageOpensAConversationAndReachesTheUserAndEveryAdmin() {
        when(roomRepository.findByUserId(1L)).thenReturn(Optional.empty());

        ChatMessageResponse sent = chatService.sendFromUser("alice", "  Phòng còn trống không?  ");

        assertThat(sent.content()).isEqualTo("Phòng còn trống không?");
        assertThat(sent.fromAdmin()).isFalse();
        assertThat(sent.roomId()).isEqualTo(100L);
        verify(messaging).convertAndSendToUser(eq("alice"), eq(SupportChatService.USER_QUEUE), any(ChatMessageResponse.class));
        verify(messaging).convertAndSendToUser(eq("admin"), eq(SupportChatService.USER_QUEUE), any(ChatMessageResponse.class));
    }

    @Test
    void conversationRemembersTheLastMessageForTheAdminList() {
        ChatRoom room = room(alice);
        when(roomRepository.findByUserId(1L)).thenReturn(Optional.of(room));

        chatService.sendFromUser("alice", "Cho mình hỏi giá phòng");

        assertThat(room.getLastMessagePreview()).isEqualTo("Cho mình hỏi giá phòng");
        assertThat(room.getLastMessageAt()).isNotNull();
    }

    @Test
    void blankOrTooLongMessagesAreRejected() {
        assertThatThrownBy(() -> chatService.sendFromUser("alice", "   ")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> chatService.sendFromUser("alice", "x".repeat(SupportChatService.MAX_MESSAGE_LENGTH + 1)))
                .isInstanceOf(RuntimeException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void adminsAnswerFromTheDashboardNotThroughTheGuestEndpoint() {
        assertThatThrownBy(() -> chatService.sendFromUser("admin", "hi")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminReplyReachesTheConversationOwnerAndOtherAdmins() {
        when(roomRepository.findById(100L)).thenReturn(Optional.of(room(alice)));

        ChatMessageResponse sent = chatService.sendFromAdmin("admin", 100L, "Dạ còn ạ");

        assertThat(sent.fromAdmin()).isTrue();
        assertThat(sent.senderUsername()).isEqualTo("admin");
        verify(messaging).convertAndSendToUser(eq("alice"), eq(SupportChatService.USER_QUEUE), any(ChatMessageResponse.class));
        verify(messaging).convertAndSendToUser(eq("admin"), eq(SupportChatService.USER_QUEUE), any(ChatMessageResponse.class));
    }

    @Test
    void replyToAnUnknownConversationIsNotFound() {
        when(roomRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendFromAdmin("admin", 404L, "hello")).isInstanceOf(NotFoundException.class);
        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void userWithoutAConversationGetsAnEmptyHistoryWithoutCreatingOne() {
        when(roomRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(chatService.getMyConversation("alice").messages()).isEmpty();
        verify(roomRepository, never()).save(any());
    }

    @Test
    void historyIsReturnedOldestFirst() {
        ChatRoom room = room(alice);
        when(roomRepository.findByUserId(1L)).thenReturn(Optional.of(room));
        ChatMessage newer = message(room, alice, "second", Instant.parse("2026-05-01T09:00:00Z"));
        ChatMessage older = message(room, admin, "first", Instant.parse("2026-05-01T08:00:00Z"));
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc(eq(100L), any())).thenReturn(List.of(newer, older));

        List<ChatMessageResponse> history = chatService.getMyConversation("alice").messages();

        assertThat(history).extracting(ChatMessageResponse::content).containsExactly("first", "second");
        assertThat(history.get(0).fromAdmin()).isTrue();
    }

    @Test
    void readingMarksOnlyTheOtherSidesMessages() {
        ChatRoom room = room(alice);
        when(roomRepository.findByUserId(1L)).thenReturn(Optional.of(room));
        when(roomRepository.findById(100L)).thenReturn(Optional.of(room));

        chatService.markReadByUser("alice");
        chatService.markReadByAdmin(100L);

        verify(messageRepository).markReadExceptSender(eq(100L), eq(1L), any());
        verify(messageRepository).markReadFromSender(eq(100L), eq(1L), any());
    }

    @Test
    void adminListShowsUnreadCountsPerConversation() {
        ChatRoom room = room(alice);
        room.setLastMessagePreview("Cho mình hỏi");
        when(roomRepository.findAllWithUnreadFromOwner()).thenReturn(List.<Object[]>of(new Object[]{room, 3L}));

        List<ChatRoomSummary> rooms = chatService.listRooms();

        assertThat(rooms).singleElement().satisfies(summary -> {
            assertThat(summary.username()).isEqualTo("alice");
            assertThat(summary.unreadCount()).isEqualTo(3L);
            assertThat(summary.lastMessagePreview()).isEqualTo("Cho mình hỏi");
        });
    }

    @Test
    void pushedPayloadDoesNotExposeEmails() {
        alice.setEmail("alice@example.com");
        when(roomRepository.findByUserId(1L)).thenReturn(Optional.of(room(alice)));

        chatService.sendFromUser("alice", "hello");

        ArgumentCaptor<ChatMessageResponse> payload = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(messaging).convertAndSendToUser(eq("alice"), anyString(), payload.capture());
        assertThat(payload.getValue().toString()).doesNotContain("alice@example.com");
    }

    private static ChatRoom room(User owner) {
        ChatRoom room = new ChatRoom();
        room.setId(100L);
        room.setUser(owner);
        return room;
    }

    private static ChatMessage message(ChatRoom room, User sender, String content, Instant at) {
        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSender(sender);
        message.setContent(content);
        message.setCreatedAt(java.time.LocalDateTime.ofInstant(at, ZoneOffset.UTC));
        return message;
    }

    private static User user(Long id, String username, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
