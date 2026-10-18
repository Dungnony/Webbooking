package com.Nhom2.booking.controller;

import com.Nhom2.booking.entity.Review;
import com.Nhom2.booking.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewService service;

    @GetMapping
    public List<Review> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<Review> create(@RequestBody Review entity, Authentication authentication) {
        return ResponseEntity.ok(service.create(authentication.getName(), entity));
    }
}
