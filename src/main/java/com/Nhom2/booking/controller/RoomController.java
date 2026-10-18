package com.Nhom2.booking.controller;

import com.Nhom2.booking.entity.Room;
import com.Nhom2.booking.service.RoomService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(
            RoomService roomService
    ) {
        this.roomService = roomService;
    }

    // thêm phòng vào khách sạn
    @PostMapping("/{hotelId}")
    public Room create(
            @PathVariable Long hotelId,
            @RequestBody Room room
    ) {
        return roomService.create(hotelId, room);
    }

    // xem phòng theo khách sạn
    @GetMapping("/hotel/{hotelId}")
    public List<Room> getByHotel(
            @PathVariable Long hotelId
    ) {
        return roomService.getByHotel(hotelId);
    }

    // sửa thông tin phòng
    @PutMapping("/{id}")
    public Room update(
            @PathVariable Long id,
            @RequestBody Room room
    ) {
        return roomService.update(id, room);
    }

    // xoá phòng
    @DeleteMapping("/{id}")
    public String delete(
            @PathVariable Long id
    ) {
        roomService.delete(id);
        return "Deleted success";
    }
}