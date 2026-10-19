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
import com.Nhom2.booking.enums.RequestStatus;
import com.Nhom2.booking.enums.RequestType;
import com.Nhom2.booking.event.BookingPaidEvent;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.BookingRequestRepository;
import com.Nhom2.booking.repository.BookingRoomRepository;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.RoomRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    private static final List<BookingStatus> RESERVED_STATUSES = List.of(BookingStatus.PENDING,
            BookingStatus.CONFIRMED);

    private final RoomRepository roomRepository;
    private final BookingRoomRepository bookingRoomRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final BookingRequestRepository bookingRequestRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public BookingService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            BookingRoomRepository bookingRoomRepository,
            BookingRequestRepository bookingRequestRepository,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.bookingRoomRepository = bookingRoomRepository;
        this.bookingRequestRepository = bookingRequestRepository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Booking createBooking(
            String username,
            Long hotelId,
            BookingRequest request) {

        validateBookingRequest(request);

        User user = getUserByUsername(username);

        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new NotFoundException("error.hotel.notFound"));

        long nights = countNights(request.getCheckInDate(), request.getCheckOutDate());

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setHotel(hotel);
        booking.setCheckInDate(request.getCheckInDate());
        booking.setCheckOutDate(request.getCheckOutDate());
        booking.setStatus(BookingStatus.PENDING);
        booking.setPaymentStatus(PaymentStatus.UNPAID);

        Booking savedBooking = bookingRepository.save(booking);

        double totalPrice = 0;
        List<BookingRoom> details = new ArrayList<>();

        for (BookingRoomRequest roomRequest : request.getRooms()) {
            validateRoomRequest(roomRequest);

            Room room = lockRoom(roomRequest.getRoomId());

            validateRoomBelongsToHotel(room, hotelId);
            validateRoomAvailable(
                    room,
                    roomRequest.getQuantity(),
                    request.getCheckInDate(),
                    request.getCheckOutDate(),
                    null);

            BookingRoom bookingRoom = new BookingRoom();
            bookingRoom.setBooking(savedBooking);
            bookingRoom.setRoom(room);
            bookingRoom.setQuantity(roomRequest.getQuantity());
            bookingRoom.setPrice(room.getPricePerNight());

            details.add(bookingRoomRepository.save(bookingRoom));
            totalPrice += room.getPricePerNight() * roomRequest.getQuantity() * nights;
        }

        savedBooking.setBookingRooms(details);
        savedBooking.setTotalPrice(totalPrice);

        Booking result = bookingRepository.save(savedBooking);
        notificationService.notifyAdmins(
                "notification.newBooking.title", "notification.newBooking.body", result.getId());

        return result;
    }

    public List<Booking> getMyBookings() {

        User user = getCurrentUser();

        return bookingRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {

        User user = getCurrentUser();
        Booking booking = getBooking(bookingId);
        validateBookingOwner(booking, user);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException("error.booking.alreadyCancelled");
        }
        if (booking.getStatus() == BookingStatus.REJECTED) {
            throw new BusinessException("error.booking.rejectedCannotCancel");
        }
        // Cancelling a paid booking means a refund, which an admin has to handle.
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("error.booking.paidNeedsRequest");
        }
        ensureBeforeCheckIn(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        Booking result = bookingRepository.save(booking);

        notificationService.notifyAdmins(
                "notification.bookingCancelled.title", "notification.bookingCancelled.body",
                user.getUsername(), booking.getId());

        return result;
    }

    public List<Booking> getBookings(BookingStatus status) {
        if (status == null) {
            return bookingRepository.findAllByOrderByCreatedAtDesc();
        }
        return bookingRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public Booking approveBooking(Long bookingId) {

        Booking booking = getBooking(bookingId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("error.booking.onlyPendingApprove");
        }

        validateAvailabilityForExistingBooking(
                booking,
                booking.getCheckInDate(),
                booking.getCheckOutDate());

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking result = bookingRepository.save(booking);

        notificationService.create(
                booking.getUser(),
                "notification.bookingApproved.title", "notification.bookingApproved.body", booking.getId());

        return result;
    }

    @Transactional
    public Booking rejectBooking(Long bookingId, String response) {

        Booking booking = getBooking(bookingId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("error.booking.onlyPendingReject");
        }

        booking.setStatus(BookingStatus.REJECTED);
        Booking result = bookingRepository.save(booking);

        notifyWithOptionalResponse(
                booking.getUser(), response,
                "notification.bookingRejected.title", "notification.bookingRejected.body", booking.getId());

        return result;
    }

    @Transactional
    public Booking markBookingPaid(Long bookingId) {

        Booking booking = getBooking(bookingId);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("error.booking.onlyConfirmedMarkPaid");
        }

        booking.setPaymentStatus(PaymentStatus.PAID);
        Booking result = bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingPaidEvent(bookingId));

        notificationService.create(
                booking.getUser(),
                "notification.paymentConfirmed.title", "notification.paymentConfirmed.body", booking.getId());

        return result;
    }

    @Transactional
    public com.Nhom2.booking.entity.BookingRequest createBookingRequest(
            String username,
            Long bookingId,
            CreateBookingRequestDto request) {

        if (request == null || request.getType() == null) {
            throw new BusinessException("error.request.typeRequired");
        }

        User user = getUserByUsername(username);
        Booking booking = getBooking(bookingId);
        validateBookingOwner(booking, user);

        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.REJECTED) {
            throw new BusinessException("error.request.bookingClosed");
        }

        if (request.getType() == RequestType.CHANGE_DATE) {
            validateBookingDates(request.getNewCheckIn(), request.getNewCheckOut());
        } else if (request.getType() == RequestType.CANCEL) {
            ensureBeforeCheckIn(booking);
        }

        com.Nhom2.booking.entity.BookingRequest entity = new com.Nhom2.booking.entity.BookingRequest();
        entity.setType(request.getType());
        entity.setNewCheckIn(request.getNewCheckIn());
        entity.setNewCheckOut(request.getNewCheckOut());
        entity.setReason(request.getReason());
        entity.setStatus(RequestStatus.PENDING);
        entity.setBooking(booking);

        com.Nhom2.booking.entity.BookingRequest result = bookingRequestRepository.save(entity);

        notificationService.notifyAdmins(
                "notification.newRequest.title",
                request.getType() == RequestType.CANCEL
                        ? "notification.newRequest.cancelBody"
                        : "notification.newRequest.changeDateBody",
                user.getUsername(), booking.getId());

        return result;
    }

    public List<com.Nhom2.booking.entity.BookingRequest> getMyBookingRequests(
            String username,
            Long bookingId) {

        User user = getUserByUsername(username);
        Booking booking = getBooking(bookingId);
        validateBookingOwner(booking, user);

        return bookingRequestRepository.findByBookingOrderByCreatedAtDesc(booking);
    }

    public List<com.Nhom2.booking.entity.BookingRequest> getBookingRequests(
            RequestStatus status) {
        if (status == null) {
            return bookingRequestRepository.findAllByOrderByCreatedAtDesc();
        }
        return bookingRequestRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public com.Nhom2.booking.entity.BookingRequest approveBookingRequest(
            Long requestId,
            String response) {

        User admin = getCurrentUser();
        com.Nhom2.booking.entity.BookingRequest request = getBookingRequest(requestId);
        ensureRequestPending(request);

        Booking booking = request.getBooking();

        if (request.getType() == RequestType.CANCEL) {
            booking.setStatus(BookingStatus.CANCELLED);
            // Approving the cancellation of a paid booking is the admin's commitment to refund it;
            // it also drops the booking from paid revenue and review eligibility.
            if (booking.getPaymentStatus() == PaymentStatus.PAID) {
                booking.setPaymentStatus(PaymentStatus.REFUNDED);
            }
        } else if (request.getType() == RequestType.CHANGE_DATE) {
            validateBookingDates(request.getNewCheckIn(), request.getNewCheckOut());
            validateAvailabilityForExistingBooking(
                    booking,
                    request.getNewCheckIn(),
                    request.getNewCheckOut());
            booking.setCheckInDate(request.getNewCheckIn());
            booking.setCheckOutDate(request.getNewCheckOut());
            recalculateTotalPrice(booking);
        }

        bookingRepository.save(booking);

        request.setStatus(RequestStatus.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(admin);
        request.setAdminResponse(response);

        com.Nhom2.booking.entity.BookingRequest result = bookingRequestRepository.save(request);

        notifyWithOptionalResponse(
                booking.getUser(), response,
                "notification.requestApproved.title", "notification.requestApproved.body", booking.getId());

        return result;
    }

    @Transactional
    public com.Nhom2.booking.entity.BookingRequest rejectBookingRequest(
            Long requestId,
            String response) {

        User admin = getCurrentUser();
        com.Nhom2.booking.entity.BookingRequest request = getBookingRequest(requestId);
        ensureRequestPending(request);

        request.setStatus(RequestStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(admin);
        request.setAdminResponse(response);

        com.Nhom2.booking.entity.BookingRequest result = bookingRequestRepository.save(request);

        notifyWithOptionalResponse(
                request.getBooking().getUser(), response,
                "notification.requestRejected.title", "notification.requestRejected.body",
                request.getBooking().getId());

        return result;
    }

    /** The admin's own response, when given, replaces the standard message text. */
    private void notifyWithOptionalResponse(
            User user, String response, String titleKey, String messageKey, Object... args) {
        if (response == null || response.isBlank()) {
            notificationService.create(user, titleKey, messageKey, args);
        } else {
            notificationService.createWithMessage(user, titleKey, response);
        }
    }

    private void validateBookingRequest(BookingRequest request) {
        if (request == null) {
            throw new BusinessException("error.booking.requestRequired");
        }
        validateBookingDates(request.getCheckInDate(), request.getCheckOutDate());
        if (request.getRooms() == null || request.getRooms().isEmpty()) {
            throw new BusinessException("error.booking.noRooms");
        }
    }

    private void validateBookingDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new BusinessException("error.booking.datesRequired");
        }
        if (!checkIn.isBefore(checkOut)) {
            throw new BusinessException("error.booking.checkInBeforeCheckOut");
        }
        if (checkIn.isBefore(LocalDate.now(clock))) {
            throw new BusinessException("error.booking.checkInPast");
        }
    }

    private void ensureBeforeCheckIn(Booking booking) {
        if (!LocalDate.now(clock).isBefore(booking.getCheckInDate())) {
            throw new BusinessException("error.booking.tooLateToCancel");
        }
    }

    private Room lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("error.room.notFound"));
    }

    private long countNights(LocalDate checkIn, LocalDate checkOut) {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    private void validateRoomRequest(BookingRoomRequest roomRequest) {
        if (roomRequest == null || roomRequest.getRoomId() == null) {
            throw new BusinessException("error.booking.roomIdRequired");
        }
        if (roomRequest.getQuantity() == null || roomRequest.getQuantity() <= 0) {
            throw new BusinessException("error.booking.roomQuantityInvalid");
        }
    }

    private void validateRoomBelongsToHotel(Room room, Long hotelId) {
        if (room.getHotel() == null || !room.getHotel().getId().equals(hotelId)) {
            throw new BusinessException("error.booking.roomNotInHotel");
        }
        if (room.getPricePerNight() == null || room.getPricePerNight() <= 0) {
            throw new BusinessException("error.room.priceInvalid");
        }
    }

    private void validateRoomAvailable(
            Room room,
            Integer requestedQuantity,
            LocalDate checkIn,
            LocalDate checkOut,
            Long excludedBookingId) {
        if (room.getQuantity() == null || room.getQuantity() <= 0) {
            throw new BusinessException("error.room.quantityInvalid");
        }

        Long reserved = excludedBookingId == null
                ? bookingRoomRepository.sumReservedQuantityForRoom(
                        room,
                        checkIn,
                        checkOut,
                        RESERVED_STATUSES)
                : bookingRoomRepository.sumReservedQuantityForRoomExcludingBooking(
                        room,
                        excludedBookingId,
                        checkIn,
                        checkOut,
                        RESERVED_STATUSES);

        int available = room.getQuantity() - reserved.intValue();

        if (requestedQuantity > available) {
            throw new BusinessException("error.booking.roomUnavailable", room.getName(), available);
        }
    }

    private void validateAvailabilityForExistingBooking(
            Booking booking,
            LocalDate checkIn,
            LocalDate checkOut) {
        for (BookingRoom bookingRoom : booking.getBookingRooms()) {
            validateRoomAvailable(
                    lockRoom(bookingRoom.getRoom().getId()),
                    bookingRoom.getQuantity(),
                    checkIn,
                    checkOut,
                    booking.getId());
        }
    }

    private void recalculateTotalPrice(Booking booking) {
        long nights = countNights(booking.getCheckInDate(), booking.getCheckOutDate());
        double totalPrice = 0;

        for (BookingRoom bookingRoom : booking.getBookingRooms()) {
            Double price = bookingRoom.getPrice() != null
                    ? bookingRoom.getPrice()
                    : bookingRoom.getRoom().getPricePerNight();
            totalPrice += price * bookingRoom.getQuantity() * nights;
        }

        booking.setTotalPrice(totalPrice);
    }

    private Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("error.booking.notFound"));
    }

    private com.Nhom2.booking.entity.BookingRequest getBookingRequest(Long requestId) {
        return bookingRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("error.request.notFound"));
    }

    private void ensureRequestPending(com.Nhom2.booking.entity.BookingRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("error.request.notPending");
        }
    }

    private void validateBookingOwner(Booking booking, User user) {
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("error.booking.notOwner");
        }
    }

    private User getCurrentUser() {
        return getUserByUsername(
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName());
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("error.user.notFound"));
    }
}
