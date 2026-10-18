package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.ReviewEligibilityResponse;
import com.Nhom2.booking.dto.ReviewPageResponse;
import com.Nhom2.booking.dto.ReviewResponse;
import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Review;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.ReviewRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    static final int MIN_RATING = 1;
    static final int MAX_RATING = 10;
    static final int MAX_PAGE_SIZE = 50;

    private static final String LOGIN_REQUIRED = "Đăng nhập để viết đánh giá.";
    private static final String ALREADY_REVIEWED = "Bạn đã đánh giá khách sạn này.";
    private static final String PAID_BOOKING_REQUIRED =
            "Chỉ khách đã đặt và thanh toán phòng tại khách sạn này mới được đánh giá.";

    @Autowired
    private ReviewRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private BookingRepository bookingRepository;

    public ReviewPageResponse getHotelReviews(Long hotelId, int page, int size) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new NotFoundException("Hotel not found");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<Review> result = repository.findByHotelIdOrderByCreatedAtDesc(hotelId, PageRequest.of(safePage, safeSize));
        return new ReviewPageResponse(
                result.map(ReviewResponse::from).getContent(),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                repository.averageRatingByHotelId(hotelId));
    }

    /** {@code username} is null for an anonymous visitor. */
    public ReviewEligibilityResponse checkEligibility(String username, Long hotelId) {
        if (username == null) {
            return new ReviewEligibilityResponse(false, LOGIN_REQUIRED);
        }
        return userRepository.findByUsername(username)
                .map(user -> {
                    String problem = eligibilityProblem(user, hotelId);
                    return new ReviewEligibilityResponse(problem == null, problem);
                })
                .orElse(new ReviewEligibilityResponse(false, LOGIN_REQUIRED));
    }

    /**
     * Tạo review cho user đang đăng nhập. Author và hotel được lấy từ DB,
     * không tin dữ liệu user/hotel lồng trong request body.
     */
    @Transactional
    public Review create(String username, Review request) {
        if (request == null || request.getHotel() == null || request.getHotel().getId() == null) {
            throw new RuntimeException("Hotel id is required");
        }
        if (request.getRating() == null || request.getRating() < MIN_RATING || request.getRating() > MAX_RATING) {
            throw new RuntimeException("Rating must be between " + MIN_RATING + " and " + MAX_RATING);
        }

        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Hotel hotel = hotelRepository.findById(request.getHotel().getId())
                .orElseThrow(() -> new NotFoundException("Hotel not found"));
        String problem = eligibilityProblem(author, hotel.getId());
        if (problem != null) {
            throw new RuntimeException(problem);
        }

        Review review = new Review();
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setUser(author);
        review.setHotel(hotel);
        Review saved = repository.save(review);

        refreshRating(hotel);
        return saved;
    }

    // Only verified guests may review, once per hotel, so ratings reflect real stays.
    private String eligibilityProblem(User user, Long hotelId) {
        if (repository.existsByUserIdAndHotelId(user.getId(), hotelId)) {
            return ALREADY_REVIEWED;
        }
        if (!bookingRepository.existsByUserIdAndHotelIdAndPaymentStatus(user.getId(), hotelId, PaymentStatus.PAID)) {
            return PAID_BOOKING_REQUIRED;
        }
        return null;
    }

    private void refreshRating(Hotel hotel) {
        hotel.setRatingAvg(repository.averageRatingByHotelId(hotel.getId()));
        hotelRepository.save(hotel);
    }
}
