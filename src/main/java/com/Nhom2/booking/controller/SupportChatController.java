package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.ChatMessageResponse;
import com.Nhom2.booking.dto.SendChatMessageRequest;
import com.Nhom2.booking.dto.SupportChatHistory;
import com.Nhom2.booking.service.SupportChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Guest side of the support chat; the AI assistant lives at /api/chat. Requires login (anyRequest().authenticated()).
@RestController
@RequestMapping("/api/support-chat")
public class SupportChatController {

    private final SupportChatService chatService;

    public SupportChatController(SupportChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/me")
    public SupportChatHistory getMyConversation(Authentication authentication) {
        return chatService.getMyConversation(authentication.getName());
    }

    @PostMapping("/me/messages")
    public ChatMessageResponse send(@RequestBody SendChatMessageRequest request, Authentication authentication) {
        return chatService.sendFromUser(authentication.getName(), request == null ? null : request.getContent());
    }

    @PostMapping("/me/read")
    public ResponseEntity<Void> markRead(Authentication authentication) {
        chatService.markReadByUser(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("count", chatService.unreadForUser(authentication.getName()));
    }
}
