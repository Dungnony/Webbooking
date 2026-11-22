package com.Nhom2.booking.repository;

import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.BookingRequest;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {

    List<BookingRequest> findAllByOrderByCreatedAtDesc();

    List<BookingRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    List<BookingRequest> findByBookingOrderByCreatedAtDesc(Booking booking);

    List<BookingRequest> findByBookingUserOrderByCreatedAtDesc(User user);

    List<BookingRequest> findByBookingAndStatus(Booking booking, RequestStatus status);

    boolean existsByBookingAndStatus(Booking booking, RequestStatus status);

    long countByStatus(RequestStatus status);
}
