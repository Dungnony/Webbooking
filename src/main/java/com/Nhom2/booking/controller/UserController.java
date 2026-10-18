package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.ChangeEmailRequest;
import com.Nhom2.booking.dto.UpdateProfileRequest;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(
            UserService userService
    ) {
        this.userService = userService;
    }

    // GET ALL
    @GetMapping
    public List<User> getAll() {

        return userService.getAll();
    }

    // Hồ sơ của user đang đăng nhập (không cần quyền ADMIN)
    @GetMapping("/me")
    public User getMe(Authentication authentication) {
        return userService.getByUsername(authentication.getName());
    }

    @PutMapping("/me")
    public User updateMe(
            @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        return userService.updateProfile(authentication.getName(), request);
    }

    // Đổi email bước 1: gửi OTP tới email mới, trả về email đã che
    @PostMapping("/me/email/otp")
    public String requestEmailChange(
            @RequestBody ChangeEmailRequest request,
            Authentication authentication
    ) {
        return userService.requestEmailChange(authentication.getName(), request.getEmail());
    }

    // Đổi email bước 2: xác nhận OTP rồi mới cập nhật email
    @PutMapping("/me/email")
    public User confirmEmailChange(
            @RequestBody ChangeEmailRequest request,
            Authentication authentication
    ) {
        return userService.confirmEmailChange(authentication.getName(), request.getEmail(), request.getOtp());
    }
    @GetMapping("/{id}")
    public Optional<User> getUser(@PathVariable Long id
    ){
       return userService.getUser(id);
    }

    // CREATE USER
    @PostMapping
    public User create(
            @RequestBody User user
    ) {

        return userService.create(user);
    }

    // DELETE USER
    @DeleteMapping("/{id}")
    public String delete(
            @PathVariable Long id
    ) {

        userService.delete(id);

        return "Deleted success";
    }

    // UPDATE USER
    @PutMapping("/{id}")
    public User update(
            @PathVariable Long id,
            @RequestBody User user
    ) {

        user.setId(id);

        return userService.create(user);
    }
}