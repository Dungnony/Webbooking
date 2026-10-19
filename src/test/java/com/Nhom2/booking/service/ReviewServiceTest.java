package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.ReviewEligibilityResponse;
import com.Nhom2.booking.dto.ReviewPageResponse;
import com.Nhom2.booking.dto.ReviewResponse;
import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Review;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.ReviewRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Messages messages;

    @InjectMocks
    private ReviewService reviewService;

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void authorIsTakenFromTheAuthenticatedUserNotTheRequestBody() {
        User author = user(1L, "alice");
        Hotel hotel = hotel(10L);
        allowReview(author, hotel);

        Review request = review(9, hotel(10L));
        request.setUser(user(2L, "bob"));

        Review saved = reviewService.create("alice", request);

        assertThat(saved.getUser()).isSameAs(author);
        assertThat(saved.getHotel()).isSameAs(hotel);
        assertThat(saved.getRating()).isEqualTo(9);
    }

    @Test
    void creatingAReviewRecomputesTheHotelRating() {
        User author = user(1L, "alice");
        Hotel hotel = hotel(10L);
        hotel.setRatingAvg(9.5);
        allowReview(author, hotel);
        when(reviewRepository.averageRatingByHotelId(10L)).thenReturn(4.0);

        reviewService.create("alice", review(2, hotel(10L)));

        ArgumentCaptor<Hotel> savedHotel = ArgumentCaptor.forClass(Hotel.class);
        verify(hotelRepository).save(savedHotel.capture());
        assertThat(savedHotel.getValue().getRatingAvg()).isEqualTo(4.0);
    }

    @Test
    void userWithoutAPaidBookingAtTheHotelCannotReview() {
        User author = user(1L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel(10L)));
        when(bookingRepository.existsByUserIdAndHotelIdAndPaymentStatus(1L, 10L, PaymentStatus.PAID)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.create("alice", review(8, hotel(10L))))
                .isInstanceOf(RuntimeException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void userCanReviewAHotelOnlyOnce() {
        User author = user(1L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel(10L)));
        when(reviewRepository.existsByUserIdAndHotelId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create("alice", review(8, hotel(10L))))
                .isInstanceOf(RuntimeException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsRatingOutsideAllowedRange() {
        assertThatThrownBy(() -> reviewService.create("alice", review(ReviewService.MAX_RATING + 1, hotel(10L))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> reviewService.create("alice", review(ReviewService.MIN_RATING - 1, hotel(10L))))
                .isInstanceOf(RuntimeException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsMissingHotel() {
        assertThatThrownBy(() -> reviewService.create("alice", review(8, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void unknownHotelIsNotFound() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice")));
        when(hotelRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create("alice", review(8, hotel(99L))))
                .isInstanceOf(NotFoundException.class);
    }

    // ── eligibility ───────────────────────────────────────────────────────────

    @Test
    void anonymousVisitorIsAskedToLogIn() {
        when(messages.get("error.review.loginRequired")).thenReturn("Sign in to write a review.");

        ReviewEligibilityResponse result = reviewService.checkEligibility(null, 10L);

        assertThat(result.canReview()).isFalse();
        assertThat(result.reason()).isEqualTo("Sign in to write a review.");
    }

    @Test
    void guestWithAPaidBookingIsEligible() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice")));
        when(bookingRepository.existsByUserIdAndHotelIdAndPaymentStatus(1L, 10L, PaymentStatus.PAID)).thenReturn(true);

        ReviewEligibilityResponse result = reviewService.checkEligibility("alice", 10L);

        assertThat(result.canReview()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void userWhoAlreadyReviewedIsNotEligible() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice")));
        when(reviewRepository.existsByUserIdAndHotelId(1L, 10L)).thenReturn(true);

        assertThat(reviewService.checkEligibility("alice", 10L).canReview()).isFalse();
    }

    // ── listing ───────────────────────────────────────────────────────────────

    @Test
    void listsReviewsOfOneHotelWithoutAuthorEmails() {
        User author = user(1L, "alice");
        author.setEmail("alice@example.com");
        Review review = review(9, hotel(10L));
        review.setId(3L);
        review.setUser(author);
        when(hotelRepository.existsById(10L)).thenReturn(true);
        when(reviewRepository.findByHotelIdOrderByCreatedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1));
        when(reviewRepository.averageRatingByHotelId(10L)).thenReturn(9.0);

        ReviewPageResponse page = reviewService.getHotelReviews(10L, 0, 10);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.averageRating()).isEqualTo(9.0);
        ReviewResponse item = page.content().get(0);
        assertThat(item.username()).isEqualTo("alice");
        assertThat(item.toString()).doesNotContain("alice@example.com");
    }

    @Test
    void pageSizeIsCapped() {
        when(hotelRepository.existsById(10L)).thenReturn(true);
        when(reviewRepository.findByHotelIdOrderByCreatedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reviewService.getHotelReviews(10L, -3, 10_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findByHotelIdOrderByCreatedAtDesc(eq(10L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(ReviewService.MAX_PAGE_SIZE);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    void listingReviewsOfAnUnknownHotelIsNotFound() {
        when(hotelRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.getHotelReviews(99L, 0, 10)).isInstanceOf(NotFoundException.class);
    }

    // ── hotel reply (admin answers on behalf of the hotel) ─────────────────────

    @Test
    void adminReplyIsStoredWithTimestampAndTheReviewerIsNotified() {
        User author = user(1L, "alice");
        Review review = review(7, hotel(10L));
        review.setId(3L);
        review.setUser(author);
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse result = reviewService.reply(3L, "  Cảm ơn bạn đã góp ý!  ");

        assertThat(result.hotelReply()).isEqualTo("Cảm ơn bạn đã góp ý!");
        assertThat(result.hotelRepliedAt()).isNotNull();
        verify(notificationService).create(eq(author), any(), any());
    }

    @Test
    void blankOrTooLongReplyIsRejected() {
        Review review = review(7, hotel(10L));
        review.setId(3L);
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.reply(3L, "   ")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> reviewService.reply(3L, "x".repeat(ReviewService.MAX_REPLY_LENGTH + 1)))
                .isInstanceOf(RuntimeException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void replyToUnknownReviewIsNotFound() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.reply(99L, "Cảm ơn")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void removingAReplyClearsTextAndTimestamp() {
        Review review = review(7, hotel(10L));
        review.setId(3L);
        review.setHotelReply("Old reply");
        review.setHotelRepliedAt(java.time.LocalDateTime.now());
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse result = reviewService.removeReply(3L);

        assertThat(result.hotelReply()).isNull();
        assertThat(result.hotelRepliedAt()).isNull();
    }

    @Test
    void adminListCanBeLimitedToUnansweredReviews() {
        when(reviewRepository.findByHotelReplyIsNullOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reviewService.getReviewsForAdmin(0, 20, true);

        verify(reviewRepository).findByHotelReplyIsNullOrderByCreatedAtDesc(any(Pageable.class));
        verify(reviewRepository, never()).findAllByOrderByCreatedAtDesc(any(Pageable.class));
    }

    private void allowReview(User author, Hotel hotel) {
        when(userRepository.findByUsername(author.getUsername())).thenReturn(Optional.of(author));
        when(hotelRepository.findById(hotel.getId())).thenReturn(Optional.of(hotel));
        when(bookingRepository.existsByUserIdAndHotelIdAndPaymentStatus(author.getId(), hotel.getId(), PaymentStatus.PAID))
                .thenReturn(true);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Review review(int rating, Hotel hotel) {
        Review review = new Review();
        review.setRating(rating);
        review.setComment("Nice stay");
        review.setHotel(hotel);
        return review;
    }

    private static Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        return hotel;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
