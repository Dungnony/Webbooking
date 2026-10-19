package com.Nhom2.booking.dto;

import java.time.LocalDateTime;

/** A notification as shown to its owner, with title and message in the caller's language. */
public record NotificationResponse(Long id, String title, String message, Boolean read, LocalDateTime createdAt) {
}
