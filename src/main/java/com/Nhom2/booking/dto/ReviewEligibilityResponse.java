package com.Nhom2.booking.dto;

/** {@code reason} explains why {@code canReview} is false and is null otherwise. */
public record ReviewEligibilityResponse(boolean canReview, String reason) {
}
