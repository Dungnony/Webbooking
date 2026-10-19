package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.ReviewEligibilityResponse;
import com.Nhom2.booking.dto.ReviewPageResponse;
import com.Nhom2.booking.dto.ReviewResponse;
import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Review;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.ReviewRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReviewService {

    static final int MIN_RATING = 1;
    static final int MAX_RATING = 10;
    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_REPLY_LENGTH = 2000;

    private static final String LOGIN_REQUIRED = "error.review.loginRequired";
    private static final String ALREADY_REVIEWED = "error.review.alreadyReviewed";
    private static final String PAID_BOOKING_REQUIRED = "error.review.paidBookingRequired";

    @Autowired
    private ReviewRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private Messages messages;

    public ReviewPageResponse getHotelReviews(Long hotelId, int page, int size) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new NotFoundException("error.hotel.notFound");
        }
        PageRequest pageRequest = safePageRequest(page, size);
        Page<Review> result = repository.findByHotelIdOrderByCreatedAtDesc(hotelId, pageRequest);
        return toPageResponse(result, pageRequest, repository.averageRatingByHotelId(hotelId));
    }

    /** All hotels, newest first; {@code onlyUnanswered} narrows it to reviews still waiting for a reply. */
    public ReviewPageResponse getReviewsForAdmin(int page, int size, boolean onlyUnanswered) {
        PageRequest pageRequest = safePageRequest(page, size);
        Page<Review> result = onlyUnanswered
                ? repository.findByHotelReplyIsNullOrderByCreatedAtDesc(pageRequest)
                : repository.findAllByOrderByCreatedAtDesc(pageRequest);
        return toPageResponse(result, pageRequest, null);
    }

    /** Admin answers on behalf of the hotel; replying again replaces the previous answer. */
    @Transactional
    public ReviewResponse reply(Long reviewId, String reply) {
        Review review = getReview(reviewId);
        String text = reply == null ? "" : reply.trim();
        if (text.isEmpty()) {
            throw new BusinessException("error.review.replyEmpty");
        }
        if (text.length() > MAX_REPLY_LENGTH) {
            throw new BusinessException("error.review.replyTooLong", MAX_REPLY_LENGTH);
        }

        review.setHotelReply(text);
        review.setHotelRepliedAt(LocalDateTime.now());
        Review saved = repository.save(review);

        if (saved.getUser() != null) {
            String hotelName = saved.getHotel() == null ? "" : saved.getHotel().getName();
            notificationService.create(
                    saved.getUser(),
                    "notification.reviewReplied.title", "notification.reviewReplied.body", hotelName);
        }
        return ReviewResponse.from(saved);
    }

    @Transactional
    public ReviewResponse removeReply(Long reviewId) {
        Review review = getReview(reviewId);
        review.setHotelReply(null);
        review.setHotelRepliedAt(null);
        return ReviewResponse.from(repository.save(review));
    }

    private Review getReview(Long reviewId) {
        return repository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("error.review.notFound"));
    }

    private static PageRequest safePageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private static ReviewPageResponse toPageResponse(Page<Review> result, PageRequest pageRequest, Double averageRating) {
        return new ReviewPageResponse(
                result.map(ReviewResponse::from).getContent(),
                pageRequest.getPageNumber(),
                pageRequest.getPageSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                averageRating);
    }

    /** {@code username} is null for an anonymous visitor. */
    public ReviewEligibilityResponse checkEligibility(String username, Long hotelId) {
        User user = username == null ? null : userRepository.findByUsername(username).orElse(null);
        String problem = user == null ? LOGIN_REQUIRED : eligibilityProblem(user, hotelId);
        return new ReviewEligibilityResponse(problem == null, problem == null ? null : messages.get(problem));
    }

    /**
     * Tạo review cho user đang đăng nhập. Author và hotel được lấy từ DB,
     * không tin dữ liệu user/hotel lồng trong request body.
     */
    @Transactional
    public Review create(String username, Review request) {
        if (request == null || request.getHotel() == null || request.getHotel().getId() == null) {
            throw new BusinessException("error.review.hotelRequired");
        }
        if (request.getRating() == null || request.getRating() < MIN_RATING || request.getRating() > MAX_RATING) {
            throw new BusinessException("error.review.ratingRange", MIN_RATING, MAX_RATING);
        }

        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("error.user.notFound"));
        Hotel hotel = hotelRepository.findById(request.getHotel().getId())
                .orElseThrow(() -> new NotFoundException("error.hotel.notFound"));
        String problem = eligibilityProblem(author, hotel.getId());
        if (problem != null) {
            throw new BusinessException(problem);
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
