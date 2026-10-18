package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Notification;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.NotificationRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private NotificationService notificationService;
    private User alice;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        notificationService = new NotificationService(notificationRepository, userRepository);

        alice = user(1L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deletesOwnNotification() {
        Notification own = notification(10L, alice);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(own));

        notificationService.delete(10L);

        verify(notificationRepository).delete(own);
    }

    @Test
    void refusesToDeleteSomeoneElsesNotification() {
        when(notificationRepository.findById(11L)).thenReturn(Optional.of(notification(11L, user(2L, "bob"))));

        assertThatThrownBy(() -> notificationService.delete(11L)).isInstanceOf(ForbiddenException.class);
        verify(notificationRepository, never()).delete(any());
    }

    @Test
    void missingNotificationIsNotFound() {
        when(notificationRepository.findById(12L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete(12L)).isInstanceOf(NotFoundException.class);
    }

    private static Notification notification(Long id, User owner) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUser(owner);
        return notification;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
