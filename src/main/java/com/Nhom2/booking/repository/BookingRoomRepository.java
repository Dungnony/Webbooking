package com.Nhom2.booking.repository;

import com.Nhom2.booking.entity.BookingRoom;
import com.Nhom2.booking.entity.Room;
import com.Nhom2.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;

public interface BookingRoomRepository extends JpaRepository<BookingRoom, Long> {

    boolean existsByRoomId(Long roomId);

    @Query("""
            select coalesce(sum(br.quantity), 0)
            from BookingRoom br
            where br.room = :room
              and br.booking.status in :statuses
              and br.booking.checkInDate < :checkOut
              and br.booking.checkOutDate > :checkIn
            """)
    Long sumReservedQuantityForRoom(
            @Param("room") Room room,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("statuses") Collection<BookingStatus> statuses
    );

    @Query("""
            select coalesce(sum(br.quantity), 0)
            from BookingRoom br
            where br.room = :room
              and br.booking.id <> :bookingId
              and br.booking.status in :statuses
              and br.booking.checkInDate < :checkOut
              and br.booking.checkOutDate > :checkIn
            """)
    Long sumReservedQuantityForRoomExcludingBooking(
            @Param("room") Room room,
            @Param("bookingId") Long bookingId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("statuses") Collection<BookingStatus> statuses
    );
}
