package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.entity.Wishlist;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.UserRepository;
import com.Nhom2.booking.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WishlistServiceTest {

    private WishlistRepository wishlistRepository;
    private HotelRepository hotelRepository;
    private WishlistService wishlistService;
    private User alice;
    private final List<Wishlist> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        wishlistRepository = mock(WishlistRepository.class);
        hotelRepository = mock(HotelRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        wishlistService = new WishlistService(wishlistRepository, userRepository, hotelRepository);

        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(hotelRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return id < 100 ? Optional.of(hotel(id)) : Optional.empty();
        });
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenAnswer(invocation -> List.copyOf(stored));
        when(wishlistRepository.existsByUserIdAndHotelId(any(), any())).thenAnswer(invocation ->
                stored.stream().anyMatch(w -> w.getHotel().getId().equals(invocation.getArgument(1))));
        when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(invocation -> {
            Wishlist wishlist = invocation.getArgument(0);
            stored.add(wishlist);
            return wishlist;
        });
    }

    @Test
    void addingAHotelStoresItForTheUser() {
        assertThat(wishlistService.add("alice", 5L)).containsExactly(5L);

        ArgumentCaptor<Wishlist> saved = ArgumentCaptor.forClass(Wishlist.class);
        verify(wishlistRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(alice);
    }

    @Test
    void addingTheSameHotelTwiceIsANoOp() {
        wishlistService.add("alice", 5L);
        wishlistService.add("alice", 5L);

        assertThat(stored).hasSize(1);
    }

    @Test
    void addingAnUnknownHotelIsNotFound() {
        assertThatThrownBy(() -> wishlistService.add("alice", 404L)).isInstanceOf(NotFoundException.class);
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void mergeAddsTheGuestWishlistAndSkipsDuplicatesAndUnknownHotels() {
        wishlistService.add("alice", 5L);

        List<Long> result = wishlistService.merge("alice", List.of(5L, 7L, 404L, 7L));

        assertThat(result).containsExactlyInAnyOrder(5L, 7L);
    }

    @Test
    void mergeOfNothingReturnsTheCurrentList() {
        wishlistService.add("alice", 5L);

        assertThat(wishlistService.merge("alice", null)).containsExactly(5L);
    }

    @Test
    void removeDeletesOnlyThatHotel() {
        wishlistService.remove("alice", 5L);

        verify(wishlistRepository).deleteByUserIdAndHotelId(1L, 5L);
    }

    @Test
    void clearDeletesEverythingOfTheUser() {
        wishlistService.clear("alice");

        verify(wishlistRepository).deleteByUserId(1L);
    }

    private static Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        return hotel;
    }
}
