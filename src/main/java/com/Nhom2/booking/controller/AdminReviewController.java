package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.ReviewPageResponse;
import com.Nhom2.booking.dto.ReviewReplyRequest;
import com.Nhom2.booking.dto.ReviewResponse;
import com.Nhom2.booking.service.ReviewService;
import org.springframework.web.bind.annotation.*;

// Under /admin/**, so SecurityConfig restricts every endpoint here to ADMIN.
@RestController
@RequestMapping("/admin/reviews")
public class AdminReviewController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ReviewPageResponse getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "false") boolean unanswered
    ) {
        return reviewService.getReviewsForAdmin(page, size, unanswered);
    }

    @PutMapping("/{reviewId}/reply")
    public ReviewResponse reply(@PathVariable Long reviewId, @RequestBody ReviewReplyRequest request) {
        return reviewService.reply(reviewId, request == null ? null : request.getReply());
    }

    @DeleteMapping("/{reviewId}/reply")
    public ReviewResponse removeReply(@PathVariable Long reviewId) {
        return reviewService.removeReply(reviewId);
    }
}
