package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Room;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.BookingRoomRepository;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final BookingRoomRepository bookingRoomRepository;

    public RoomService(
            RoomRepository roomRepository,
            HotelRepository hotelRepository,
            BookingRoomRepository bookingRoomRepository
    ) {
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
        this.bookingRoomRepository = bookingRoomRepository;
    }

    public Room create(Long hotelId, Room room) {
        validateRoomFields(room);
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new NotFoundException("Hotel not found"));

        room.setHotel(hotel);
        return roomRepository.save(room);
    }

    /**
     * Existing bookings keep the price stored on their BookingRoom, so changing the price only affects new bookings.
     */
    public Room update(Long id, Room changes) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        validateRoomFields(changes);

        room.setName(changes.getName().trim());
        room.setType(changes.getType());
        room.setPricePerNight(changes.getPricePerNight());
        room.setCapacity(changes.getCapacity());
        room.setQuantity(changes.getQuantity());
        room.setDescription(changes.getDescription());
        return roomRepository.save(room);
    }

    public List<Room> getByHotel(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new NotFoundException("Hotel not found"));

        return roomRepository.findByHotel(hotel);
    }

    public void delete(Long id) {
        if (!roomRepository.existsById(id)) {
            throw new NotFoundException("Room not found");
        }
        // Past bookings reference the room; deleting it would break their history (and the foreign key).
        if (bookingRoomRepository.existsByRoomId(id)) {
            throw new RuntimeException(
                    "Phòng đã có lịch sử đặt nên không thể xóa. Hãy đặt số lượng về 0 để ngừng nhận đặt phòng.");
        }
        roomRepository.deleteById(id);
    }

    public List<Room> getAll() {
        return roomRepository.findAll();
    }

    public Room save(Room room) {
        return roomRepository.save(room);
    }

    private void validateRoomFields(Room room) {
        if (room == null || room.getName() == null || room.getName().isBlank()) {
            throw new RuntimeException("Tên phòng không được trống");
        }
        if (room.getPricePerNight() == null || room.getPricePerNight() <= 0) {
            throw new RuntimeException("Giá phòng phải lớn hơn 0");
        }
        if (room.getCapacity() == null || room.getCapacity() < 1) {
            throw new RuntimeException("Sức chứa phải từ 1 người trở lên");
        }
        // Zero is allowed: it keeps the room and its history but stops new bookings.
        if (room.getQuantity() == null || room.getQuantity() < 0) {
            throw new RuntimeException("Số lượng phòng không được âm");
        }
    }
}
