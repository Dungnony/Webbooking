package com.Nhom2.booking.repository;

import com.Nhom2.booking.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByHotelIdOrderByCreatedAtDesc(Long hotelId, Pageable pageable);

    boolean existsByUserIdAndHotelId(Long userId, Long hotelId);

    @Query("select avg(r.rating) from Review r where r.hotel.id = :hotelId")
    Double averageRatingByHotelId(@Param("hotelId") Long hotelId);
}
