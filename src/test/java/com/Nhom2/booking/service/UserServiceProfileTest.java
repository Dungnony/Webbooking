package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.UpdateProfileRequest;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.UserRepository;
import com.Nhom2.booking.security.GoogleTokenVerifier;
import com.Nhom2.booking.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceProfileTest {

    private static final String STRONG_PASSWORD = "NewPass#2026";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;
    private User existing;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(
                userRepository,
                mock(JwtUtil.class),
                mock(MailService.class),
                mock(OtpService.class),
                passwordEncoder,
                mock(GoogleTokenVerifier.class));

        existing = new User();
        existing.setId(5L);
        existing.setUsername("alice");
        existing.setEmail("old@example.com");
        existing.setPassword("$2a$old-hash");
        existing.setRole(UserRole.USER);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void encodesNewPasswordAndLeavesOtherFieldsUntouched() {
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("$2a$new-hash");

        User saved = userService.updateProfile("alice", new UpdateProfileRequest(STRONG_PASSWORD));

        assertThat(saved.getPassword()).isEqualTo("$2a$new-hash");
        assertThat(saved.getEmail()).isEqualTo("old@example.com");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> userService.updateProfile("alice", new UpdateProfileRequest("weak")))
                .isInstanceOf(RuntimeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsMissingPassword() {
        assertThatThrownBy(() -> userService.updateProfile("alice", new UpdateProfileRequest("  ")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> userService.updateProfile("alice", null))
                .isInstanceOf(RuntimeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByUsername("ghost")).isInstanceOf(NotFoundException.class);
    }
}
