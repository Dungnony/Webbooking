package com.Nhom2.booking.dto;

import java.time.LocalDateTime;

/** One row of the admin conversation list; {@code unreadCount} counts the guest's messages no admin has seen. */
public record ChatRoomSummary(
        Long roomId,
        String username,
        LocalDateTime lastMessageAt,
        String lastMessagePreview,
        long unreadCount) {
}
