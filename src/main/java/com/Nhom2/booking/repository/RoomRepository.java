package com.Nhom2.booking.repository;

import com.Nhom2.booking.entity.Room;
import com.Nhom2.booking.entity.Hotel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByHotel(Hotel hotel);

    /**
     * SELECT ... FOR UPDATE: concurrent bookings of the same room wait here until the first transaction
     * commits, so they see each other's reservations and cannot both take the last unit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}