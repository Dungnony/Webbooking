package com.Nhom2.booking.controller;

import com.Nhom2.booking.service.WishlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Every endpoint works on the logged-in user's own wishlist and returns its hotel ids.
@RestController
@RequestMapping("/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public List<Long> get(Authentication authentication) {
        return wishlistService.getHotelIds(authentication.getName());
    }

    @PutMapping("/{hotelId}")
    public List<Long> add(@PathVariable Long hotelId, Authentication authentication) {
        return wishlistService.add(authentication.getName(), hotelId);
    }

    @DeleteMapping("/{hotelId}")
    public List<Long> remove(@PathVariable Long hotelId, Authentication authentication) {
        return wishlistService.remove(authentication.getName(), hotelId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(Authentication authentication) {
        wishlistService.clear(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // Called once after login with the hotel ids the visitor saved in the browser.
    @PostMapping("/merge")
    public List<Long> merge(@RequestBody List<Long> hotelIds, Authentication authentication) {
        return wishlistService.merge(authentication.getName(), hotelIds);
    }
}
