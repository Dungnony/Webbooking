package com.Nhom2.booking.repository;

import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUser(User user);

    List<Booking> findByUserOrderByCreatedAtDesc(User user);

    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);

    List<Booking> findByStatusAndPaymentStatusAndCreatedAtBefore(
            BookingStatus status,
            PaymentStatus paymentStatus,
            LocalDateTime createdBefore);

    long countByStatus(BookingStatus status);

    @Query("select coalesce(sum(b.totalPrice), 0) from Booking b where b.paymentStatus = :paymentStatus")
    Double sumTotalPriceByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);
}
