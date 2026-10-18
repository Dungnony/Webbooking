package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Review;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.ReviewRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void authorIsTakenFromTheAuthenticatedUserNotTheRequestBody() {
        User author = user(1L, "alice");
        User impersonated = user(2L, "bob");
        Hotel hotel = hotel(10L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review request = review(9, hotel(10L));
        request.setUser(impersonated);

        Review saved = reviewService.create("alice", request);

        assertThat(saved.getUser()).isSameAs(author);
        assertThat(saved.getHotel()).isSameAs(hotel);
        assertThat(saved.getRating()).isEqualTo(9);
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
