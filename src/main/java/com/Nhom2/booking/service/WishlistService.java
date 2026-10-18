package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.entity.Wishlist;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.UserRepository;
import com.Nhom2.booking.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Server-side wishlist so it follows the user across devices. Every operation returns hotel ids, newest first. */
@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;

    public WishlistService(
            WishlistRepository wishlistRepository,
            UserRepository userRepository,
            HotelRepository hotelRepository
    ) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
    }

    public List<Long> getHotelIds(String username) {
        return hotelIdsOf(getUser(username));
    }

    @Transactional
    public List<Long> add(String username, Long hotelId) {
        User user = getUser(username);
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new NotFoundException("Hotel not found"));
        addIfMissing(user, hotel);
        return hotelIdsOf(user);
    }

    /**
     * Folds the wishlist a visitor built before logging in into their account.
     * Hotels that no longer exist are skipped rather than failing the whole login sync.
     */
    @Transactional
    public List<Long> merge(String username, List<Long> hotelIds) {
        User user = getUser(username);
        if (hotelIds != null) {
            for (Long hotelId : new LinkedHashSet<>(hotelIds)) {
                Optional<Hotel> hotel = hotelId == null ? Optional.empty() : hotelRepository.findById(hotelId);
                hotel.ifPresent(h -> addIfMissing(user, h));
            }
        }
        return hotelIdsOf(user);
    }

    @Transactional
    public List<Long> remove(String username, Long hotelId) {
        User user = getUser(username);
        wishlistRepository.deleteByUserIdAndHotelId(user.getId(), hotelId);
        return hotelIdsOf(user);
    }

    @Transactional
    public void clear(String username) {
        wishlistRepository.deleteByUserId(getUser(username).getId());
    }

    private void addIfMissing(User user, Hotel hotel) {
        if (!wishlistRepository.existsByUserIdAndHotelId(user.getId(), hotel.getId())) {
            wishlistRepository.save(new Wishlist(user, hotel));
        }
    }

    private List<Long> hotelIdsOf(User user) {
        return wishlistRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(wishlist -> wishlist.getHotel().getId())
                .toList();
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
