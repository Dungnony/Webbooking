package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.ChatMessageResponse;
import com.Nhom2.booking.dto.ChatRoomSummary;
import com.Nhom2.booking.dto.SupportChatHistory;
import com.Nhom2.booking.entity.ChatMessage;
import com.Nhom2.booking.entity.ChatRoom;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.ChatMessageRepository;
import com.Nhom2.booking.repository.ChatRoomRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Support chat between guests and admins. Messages are written through the REST API and pushed over WebSocket
 * to each participant's private queue (/user/queue/support-chat), so nobody can subscribe to another conversation.
 */
@Service
public class SupportChatService {

    public static final String USER_QUEUE = "/queue/support-chat";
    static final int MAX_MESSAGE_LENGTH = 2000;
    static final int HISTORY_SIZE = 50;
    private static final int PREVIEW_LENGTH = 100;

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public SupportChatService(
            ChatRoomRepository roomRepository,
            ChatMessageRepository messageRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messaging,
            Clock clock
    ) {
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messaging = messaging;
        this.clock = clock;
    }

    // ── guest side ────────────────────────────────────────────────────────────

    public SupportChatHistory getMyConversation(String username) {
        User user = getUser(username);
        return roomRepository.findByUserId(user.getId())
                .map(room -> new SupportChatHistory(room.getId(), recentMessages(room.getId())))
                .orElse(new SupportChatHistory(null, List.of()));
    }

    @Transactional
    public ChatMessageResponse sendFromUser(String username, String content) {
        String text = validate(content);
        User user = getUser(username);
        if (user.getRole() == UserRole.ADMIN) {
            throw new ForbiddenException("error.chat.adminUseDashboard");
        }
        ChatRoom room = roomRepository.findByUserId(user.getId()).orElseGet(() -> openRoom(user));
        return post(room, user, text);
    }

    @Transactional
    public void markReadByUser(String username) {
        User user = getUser(username);
        roomRepository.findByUserId(user.getId()).ifPresent(room ->
                messageRepository.markReadExceptSender(room.getId(), user.getId(), now()));
    }

    public long unreadForUser(String username) {
        return messageRepository.countUnreadForOwner(getUser(username).getId());
    }

    // ── admin side ────────────────────────────────────────────────────────────

    public List<ChatRoomSummary> listRooms() {
        return roomRepository.findAllWithUnreadFromOwner().stream()
                .map(row -> {
                    ChatRoom room = (ChatRoom) row[0];
                    return new ChatRoomSummary(
                            room.getId(),
                            room.getUser().getUsername(),
                            room.getLastMessageAt(),
                            room.getLastMessagePreview(),
                            ((Number) row[1]).longValue());
                })
                .toList();
    }

    public List<ChatMessageResponse> getRoomMessages(Long roomId) {
        return recentMessages(getRoom(roomId).getId());
    }

    @Transactional
    public ChatMessageResponse sendFromAdmin(String adminUsername, Long roomId, String content) {
        String text = validate(content);
        ChatRoom room = getRoom(roomId);
        return post(room, getUser(adminUsername), text);
    }

    @Transactional
    public void markReadByAdmin(Long roomId) {
        ChatRoom room = getRoom(roomId);
        messageRepository.markReadFromSender(room.getId(), room.getUser().getId(), now());
    }

    public long unreadForAdmins() {
        return messageRepository.countUnreadForAdmins();
    }

    // ── shared ────────────────────────────────────────────────────────────────

    private ChatMessageResponse post(ChatRoom room, User sender, String text) {
        LocalDateTime now = now();
        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSender(sender);
        message.setContent(text);
        message.setCreatedAt(now);
        ChatMessage saved = messageRepository.save(message);

        room.setLastMessageAt(now);
        room.setLastMessagePreview(text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…");
        roomRepository.save(room);

        ChatMessageResponse response = ChatMessageResponse.from(saved);
        afterCommit(() -> push(room, response));
        return response;
    }

    // The guest and every admin get the message, so several admin tabs stay in sync.
    private void push(ChatRoom room, ChatMessageResponse message) {
        messaging.convertAndSendToUser(room.getUser().getUsername(), USER_QUEUE, message);
        for (User admin : userRepository.findByRole(UserRole.ADMIN)) {
            messaging.convertAndSendToUser(admin.getUsername(), USER_QUEUE, message);
        }
    }

    // Pushing before commit could show a message that is then rolled back.
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private ChatRoom openRoom(User user) {
        ChatRoom room = new ChatRoom();
        room.setUser(user);
        room.setCreatedAt(now());
        return roomRepository.save(room);
    }

    private List<ChatMessageResponse> recentMessages(Long roomId) {
        List<ChatMessageResponse> newestFirst = new ArrayList<>(
                messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, HISTORY_SIZE)).stream()
                        .map(ChatMessageResponse::from)
                        .toList());
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    private static String validate(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException("error.chat.messageEmpty");
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("error.chat.messageTooLong", MAX_MESSAGE_LENGTH);
        }
        return text;
    }

    private ChatRoom getRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("error.chat.notFound"));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("error.user.notFound"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
