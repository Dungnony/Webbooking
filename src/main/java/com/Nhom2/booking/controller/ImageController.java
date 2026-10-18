package com.Nhom2.booking.controller;

import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Room;
import com.Nhom2.booking.entity.RoomImage;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.RoomRepository;
import com.Nhom2.booking.service.ImageStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final ImageStorageService imageStorage;

    public ImageController(
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            ImageStorageService imageStorage
    ) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.imageStorage = imageStorage;
    }

    /**
     * Upload ảnh đại diện cho hotel
     * POST /api/images/hotel/{hotelId}
     */
    @PostMapping(value = "/hotel/{hotelId}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadHotelImage(
            @PathVariable Long hotelId,
            @RequestParam("file") MultipartFile file
    ) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new NotFoundException("Hotel not found: " + hotelId));

        String imageUrl = imageStorage.store(file, "hotels");
        hotel.setImageUrl(imageUrl);
        hotelRepository.save(hotel);

        return ResponseEntity.ok(Map.of(
                "message", "Upload thành công",
                "imageUrl", imageUrl
        ));
    }

    /**
     * Upload ảnh cho room
     * POST /api/images/room/{roomId}
     */
    @PostMapping("/room/{roomId}")
    public ResponseEntity<?> uploadRoomImage(
            @PathVariable Long roomId,
            @RequestParam("file") MultipartFile file
    ) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found: " + roomId));

        String imageUrl = imageStorage.store(file, "rooms");

        RoomImage roomImage = new RoomImage();
        roomImage.setImageUrl(imageUrl);
        roomImage.setRoom(room);

        if (room.getImages() == null) {
            room.setImages(new java.util.HashSet<>());
        }
        room.getImages().add(roomImage);
        roomRepository.save(room);

        return ResponseEntity.ok(Map.of(
                "message", "Upload thành công",
                "imageUrl", imageUrl
        ));
    }
}
