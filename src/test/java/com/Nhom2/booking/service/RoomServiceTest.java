package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Room;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.BookingRoomRepository;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomServiceTest {

    private RoomRepository roomRepository;
    private BookingRoomRepository bookingRoomRepository;
    private RoomService roomService;
    private Room existing;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        bookingRoomRepository = mock(BookingRoomRepository.class);
        roomService = new RoomService(roomRepository, mock(HotelRepository.class), bookingRoomRepository);

        Hotel hotel = new Hotel();
        hotel.setId(10L);
        existing = room("Deluxe", 500_000.0, 2, 3);
        existing.setId(100L);
        existing.setHotel(hotel);

        when(roomRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(roomRepository.existsById(100L)).thenReturn(true);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateChangesEditableFieldsAndKeepsTheHotel() {
        Room changes = room("Deluxe Sea View", 650_000.0, 3, 5);
        changes.setType("Deluxe");
        changes.setDescription("Ban công hướng biển");

        Room updated = roomService.update(100L, changes);

        assertThat(updated.getName()).isEqualTo("Deluxe Sea View");
        assertThat(updated.getPricePerNight()).isEqualTo(650_000.0);
        assertThat(updated.getCapacity()).isEqualTo(3);
        assertThat(updated.getQuantity()).isEqualTo(5);
        assertThat(updated.getDescription()).isEqualTo("Ban công hướng biển");
        assertThat(updated.getHotel().getId()).isEqualTo(10L);
    }

    @Test
    void quantityZeroIsAllowedToStopTakingBookings() {
        assertThat(roomService.update(100L, room("Deluxe", 500_000.0, 2, 0)).getQuantity()).isZero();
    }

    @Test
    void updateRejectsInvalidValues() {
        assertThatThrownBy(() -> roomService.update(100L, room("Deluxe", 0.0, 2, 1))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> roomService.update(100L, room("Deluxe", 500_000.0, 0, 1))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> roomService.update(100L, room("Deluxe", 500_000.0, 2, -1))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> roomService.update(100L, room(" ", 500_000.0, 2, 1))).isInstanceOf(RuntimeException.class);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void updatingAnUnknownRoomIsNotFound() {
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.update(999L, room("Deluxe", 500_000.0, 2, 1)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void roomWithBookingHistoryCannotBeDeleted() {
        when(bookingRoomRepository.existsByRoomId(100L)).thenReturn(true);

        assertThatThrownBy(() -> roomService.delete(100L)).isInstanceOf(RuntimeException.class);
        verify(roomRepository, never()).deleteById(anyLong());
    }

    @Test
    void roomWithoutBookingsIsDeleted() {
        when(bookingRoomRepository.existsByRoomId(100L)).thenReturn(false);

        roomService.delete(100L);

        verify(roomRepository).deleteById(100L);
    }

    private static Room room(String name, Double price, Integer capacity, Integer quantity) {
        Room room = new Room();
        room.setName(name);
        room.setPricePerNight(price);
        room.setCapacity(capacity);
        room.setQuantity(quantity);
        return room;
    }
}
