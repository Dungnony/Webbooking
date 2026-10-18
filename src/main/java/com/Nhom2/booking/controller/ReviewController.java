package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.ReviewResponse;
import com.Nhom2.booking.entity.Review;
import com.Nhom2.booking.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Reading reviews lives in HotelReviewController (GET /hotels/{hotelId}/reviews), paginated per hotel.
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewService service;

    @PostMapping
    public ResponseEntity<ReviewResponse> create(@RequestBody Review entity, Authentication authentication) {
        return ResponseEntity.ok(ReviewResponse.from(service.create(authentication.getName(), entity)));
    }
}
