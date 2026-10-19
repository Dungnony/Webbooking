package com.Nhom2.booking.dto;

import com.Nhom2.booking.entity.ChatMessage;

import java.time.LocalDateTime;

/** Sent over REST and pushed over WebSocket; carries usernames only, never emails. */
public record ChatMessageResponse(
        Long id,
        Long roomId,
        String senderUsername,
        boolean fromAdmin,
        String content,
        LocalDateTime createdAt,
        LocalDateTime readAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        // Anyone other than the conversation owner answering in it is an admin.
        boolean fromAdmin = !message.getSender().getId().equals(message.getRoom().getUser().getId());
        return new ChatMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getSender().getUsername(),
                fromAdmin,
                message.getContent(),
                message.getCreatedAt(),
                message.getReadAt());
    }
}
