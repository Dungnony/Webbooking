package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.ReviewEligibilityResponse;
import com.Nhom2.booking.dto.ReviewPageResponse;
import com.Nhom2.booking.service.ReviewService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Under GET /hotels/**, so anonymous visitors can read reviews too.
@RestController
@RequestMapping("/hotels/{hotelId}")
public class HotelReviewController {

    private static final String DEFAULT_PAGE_SIZE = "10";

    private final ReviewService reviewService;

    public HotelReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/reviews")
    public ReviewPageResponse getReviews(
            @PathVariable Long hotelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return reviewService.getHotelReviews(hotelId, page, size);
    }

    @GetMapping("/review-eligibility")
    public ReviewEligibilityResponse getEligibility(@PathVariable Long hotelId, Authentication authentication) {
        boolean loggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
        return reviewService.checkEligibility(loggedIn ? authentication.getName() : null, hotelId);
    }
}
