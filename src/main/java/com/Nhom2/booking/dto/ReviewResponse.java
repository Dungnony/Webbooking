package com.Nhom2.booking.dto;

import com.Nhom2.booking.entity.Review;

import java.time.LocalDateTime;

/** Public view of a review: exposes the author's username only, never their email. */
public record ReviewResponse(Long id, Integer rating, String comment, LocalDateTime createdAt, String username) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                review.getUser() == null ? null : review.getUser().getUsername());
    }
}
