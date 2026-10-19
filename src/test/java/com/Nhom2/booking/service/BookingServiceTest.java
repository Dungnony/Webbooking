package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.BookingRequest;
import com.Nhom2.booking.dto.BookingRoomRequest;
import com.Nhom2.booking.dto.CreateBookingRequestDto;
import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.BookingRoom;
import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Room;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.enums.RequestType;
import com.Nhom2.booking.event.BookingPaidEvent;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.BookingRequestRepository;
import com.Nhom2.booking.repository.BookingRoomRepository;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.RoomRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

class BookingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    private BookingRepository bookingRepository;
    private RoomRepository roomRepository;
    private BookingRoomRepository bookingRoomRepository;
    private BookingRequestRepository bookingRequestRepository;
    private ApplicationEventPublisher eventPublisher;
    private BookingService bookingService;
    private User alice;
    private Hotel hotel;
    private Room room;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        roomRepository = mock(RoomRepository.class);
        bookingRoomRepository = mock(BookingRoomRepository.class);
        bookingRequestRepository = mock(BookingRequestRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        HotelRepository hotelRepository = mock(HotelRepository.class);

        eventPublisher = mock(ApplicationEventPublisher.class);
        bookingService = new BookingService(
                bookingRepository, userRepository, hotelRepository, roomRepository, bookingRoomRepository,
                bookingRequestRepository, mock(NotificationService.class), eventPublisher,
                Clock.fixed(Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC));

        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        hotel = new Hotel();
        hotel.setId(10L);
        room = new Room();
        room.setId(100L);
        room.setName("Deluxe");
        room.setHotel(hotel);
        room.setQuantity(1);
        room.setPricePerNight(500_000.0);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));
        when(roomRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRoomRepository.save(any(BookingRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRoomRepository.sumReservedQuantityForRoom(any(), any(), any(), any())).thenReturn(0L);
        when(bookingRoomRepository.sumReservedQuantityForRoomExcludingBooking(any(), anyLong(), any(), any(), any()))
                .thenReturn(0L);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── A2: availability is checked against a row-locked room ──────────────────
    // The actual race protection is the database row lock (SELECT ... FOR UPDATE), which a unit test
    // cannot exercise; these tests pin that every availability check loads the room through the lock.

    @Test
    void creatingABookingLocksTheRoomBeforeCheckingAvailability() {
        bookingService.createBooking("alice", 10L, bookingRequest(TODAY.plusDays(1), TODAY.plusDays(3)));

        verify(roomRepository).findByIdForUpdate(100L);
        verify(roomRepository, never()).findById(anyLong());
    }

    @Test
    void approvingABookingRechecksAvailabilityOnTheLockedRoom() {
        Booking booking = existingBooking(BookingStatus.PENDING, PaymentStatus.UNPAID, TODAY.plusDays(5));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        bookingService.approveBooking(7L);

        verify(roomRepository).findByIdForUpdate(100L);
    }

    @Test
    void bookingIsRejectedWhenTheLockedRoomIsFull() {
        when(bookingRoomRepository.sumReservedQuantityForRoom(any(), any(), any(), any())).thenReturn(1L);

        assertThatThrownBy(() -> bookingService.createBooking(
                "alice", 10L, bookingRequest(TODAY.plusDays(1), TODAY.plusDays(3))))
                .isInstanceOf(RuntimeException.class);
    }

    // ── A3: cancellation rules ─────────────────────────────────────────────────

    @Test
    void unpaidBookingCanBeCancelledBeforeCheckIn() {
        Booking booking = existingBooking(BookingStatus.PENDING, PaymentStatus.UNPAID, TODAY.plusDays(1));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        assertThat(bookingService.cancelBooking(7L).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void bookingCannotBeCancelledOnOrAfterTheCheckInDate() {
        Booking today = existingBooking(BookingStatus.CONFIRMED, PaymentStatus.UNPAID, TODAY);
        Booking past = existingBooking(BookingStatus.CONFIRMED, PaymentStatus.UNPAID, TODAY.minusDays(2));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(today));
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(past));

        assertThatThrownBy(() -> bookingService.cancelBooking(7L)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> bookingService.cancelBooking(8L)).isInstanceOf(RuntimeException.class);
        assertThat(today.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void paidBookingMustGoThroughACancelRequest() {
        Booking booking = existingBooking(BookingStatus.CONFIRMED, PaymentStatus.PAID, TODAY.plusDays(3));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(7L)).isInstanceOf(RuntimeException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        CreateBookingRequestDto cancelRequest = new CreateBookingRequestDto();
        cancelRequest.setType(RequestType.CANCEL);
        cancelRequest.setReason("Change of plans");
        assertThat(bookingService.createBookingRequest("alice", 7L, cancelRequest)).isNotNull();
    }

    @Test
    void cancelRequestIsRejectedOnOrAfterTheCheckInDate() {
        Booking booking = existingBooking(BookingStatus.CONFIRMED, PaymentStatus.PAID, TODAY);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        CreateBookingRequestDto cancelRequest = new CreateBookingRequestDto();
        cancelRequest.setType(RequestType.CANCEL);

        assertThatThrownBy(() -> bookingService.createBookingRequest("alice", 7L, cancelRequest))
                .isInstanceOf(RuntimeException.class);
        verify(bookingRequestRepository, never()).save(any());
    }

    // ── invoice trigger ────────────────────────────────────────────────────────

    @Test
    void adminMarkingABookingPaidTriggersTheInvoiceEmail() {
        Booking booking = existingBooking(BookingStatus.CONFIRMED, PaymentStatus.UNPAID, TODAY.plusDays(5));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        bookingService.markBookingPaid(7L);

        verify(eventPublisher).publishEvent(new BookingPaidEvent(7L));
    }

    private Booking existingBooking(BookingStatus status, PaymentStatus paymentStatus, LocalDate checkIn) {
        Booking booking = new Booking();
        booking.setId(7L);
        booking.setUser(alice);
        booking.setHotel(hotel);
        booking.setStatus(status);
        booking.setPaymentStatus(paymentStatus);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkIn.plusDays(2));
        BookingRoom bookingRoom = new BookingRoom();
        bookingRoom.setRoom(room);
        bookingRoom.setQuantity(1);
        bookingRoom.setPrice(room.getPricePerNight());
        booking.setBookingRooms(new ArrayList<>(List.of(bookingRoom)));
        return booking;
    }

    private static BookingRequest bookingRequest(LocalDate checkIn, LocalDate checkOut) {
        BookingRoomRequest roomRequest = new BookingRoomRequest();
        roomRequest.setRoomId(100L);
        roomRequest.setQuantity(1);
        BookingRequest request = new BookingRequest();
        request.setCheckInDate(checkIn);
        request.setCheckOutDate(checkOut);
        request.setRooms(List.of(roomRequest));
        return request;
    }
}
