package com.Nhom2.booking.dto;

import java.util.List;

/** One page of a hotel's reviews; {@code averageRating} covers all of them, not just this page. */
public record ReviewPageResponse(
        List<ReviewResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Double averageRating) {
}
