package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.ChatMessageResponse;
import com.Nhom2.booking.dto.ChatRoomSummary;
import com.Nhom2.booking.dto.SendChatMessageRequest;
import com.Nhom2.booking.service.SupportChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Under /admin/**, so SecurityConfig restricts every endpoint here to ADMIN.
@RestController
@RequestMapping("/admin/support-chat")
public class AdminSupportChatController {

    private final SupportChatService chatService;

    public AdminSupportChatController(SupportChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/rooms")
    public List<ChatRoomSummary> listRooms() {
        return chatService.listRooms();
    }

    @GetMapping("/rooms/{roomId}/messages")
    public List<ChatMessageResponse> getMessages(@PathVariable Long roomId) {
        return chatService.getRoomMessages(roomId);
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ChatMessageResponse reply(
            @PathVariable Long roomId,
            @RequestBody SendChatMessageRequest request,
            Authentication authentication
    ) {
        return chatService.sendFromAdmin(authentication.getName(), roomId, request == null ? null : request.getContent());
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long roomId) {
        chatService.markReadByAdmin(roomId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", chatService.unreadForAdmins());
    }
}
