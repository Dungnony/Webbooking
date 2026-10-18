package com.Nhom2.booking.controller;

import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Room;
import com.Nhom2.booking.entity.RoomImage;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.RoomRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    // Thư mục lưu ảnh upload
    private static final String UPLOAD_DIR = "uploads";

    // Uploads are served publicly by extension, so anything renderable as active content (html, svg) is refused.
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    public ImageController(HotelRepository hotelRepository, RoomRepository roomRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
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

        String imageUrl = saveFile(file, "hotels");
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

        String imageUrl = saveFile(file, "rooms");

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

    /**
     * Lưu file vào thư mục uploads/{subDir}/
     * Trả về đường dẫn URL tương đối
     */
    private String saveFile(MultipartFile file, String subDir) {
        if (file.isEmpty()) {
            throw new RuntimeException("File rỗng!");
        }
        String extension = resolveImageExtension(file.getOriginalFilename());

        try {
            // Tạo thư mục nếu chưa có
            Path uploadPath = Paths.get(UPLOAD_DIR, subDir);
            Files.createDirectories(uploadPath);

            // Tạo tên file unique
            String newFileName = UUID.randomUUID().toString() + extension;

            // Lưu file
            Path filePath = uploadPath.resolve(newFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Trả về URL tương đối (sẽ được serve bởi WebConfig)
            return "/" + UPLOAD_DIR + "/" + subDir + "/" + newFileName;

        } catch (IOException e) {
            throw new IllegalStateException("Lỗi khi lưu file: " + e.getMessage(), e);
        }
    }

    /**
     * Trả về phần mở rộng (lowercase, có dấu chấm) nếu là định dạng ảnh được phép.
     */
    static String resolveImageExtension(String originalName) {
        int dotIndex = originalName == null ? -1 : originalName.lastIndexOf('.');
        String extension = dotIndex < 0 ? "" : originalName.substring(dotIndex).toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("Chỉ chấp nhận ảnh định dạng: " + String.join(", ", ALLOWED_IMAGE_EXTENSIONS));
        }
        return extension;
    }
}
