package com.Nhom2.booking.repository;

import com.Nhom2.booking.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    List<Wishlist> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndHotelId(Long userId, Long hotelId);

    @Transactional
    void deleteByUserIdAndHotelId(Long userId, Long hotelId);

    @Transactional
    void deleteByUserId(Long userId);
}
