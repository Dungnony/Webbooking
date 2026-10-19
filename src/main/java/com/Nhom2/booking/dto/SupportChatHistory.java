package com.Nhom2.booking.dto;

import java.util.List;

/** {@code roomId} is null until the user sends their first message; messages are oldest first. */
public record SupportChatHistory(Long roomId, List<ChatMessageResponse> messages) {
}
