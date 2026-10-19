package com.Nhom2.booking.service;

import com.Nhom2.booking.config.LocaleConfig;
import com.Nhom2.booking.dto.NotificationResponse;
import com.Nhom2.booking.entity.Notification;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.repository.NotificationRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        notificationService = new NotificationService(
                notificationRepository, userRepository, new Messages(LocaleConfig.createMessageSource()));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alice = user(1L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void systemNotificationIsShownInTheReadersCurrentLanguage() {
        Notification stored = notificationService.create(
                alice, "notification.bookingApproved.title", "notification.bookingApproved.body", 7L);

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        NotificationResponse english = notificationService.toResponse(stored);
        LocaleContextHolder.setLocale(LocaleConfig.DEFAULT_LOCALE);
        NotificationResponse vietnamese = notificationService.toResponse(stored);

        assertThat(english.title()).isEqualTo("Booking approved");
        assertThat(english.message()).isEqualTo("Your booking #7 has been approved.");
        assertThat(vietnamese.message()).isEqualTo("Đơn #7 của bạn đã được duyệt.");
    }

    @Test
    void storedTextIsAVietnameseRenderingSoTheRowStaysReadable() {
        Notification stored = notificationService.create(
                alice, "notification.bookingApproved.title", "notification.bookingApproved.body", 7L);

        assertThat(stored.getTitle()).isEqualTo("Đơn đã được duyệt");
        assertThat(stored.getMessage()).isEqualTo("Đơn #7 của bạn đã được duyệt.");
        assertThat(stored.getMessageArgs()).containsExactly("7");
    }

    @Test
    void adminWrittenMessageIsShownAsWrittenWithATranslatedTitle() {
        Notification stored = notificationService.createWithMessage(
                alice, "notification.bookingRejected.title", "Khách sạn đã hết phòng ngày này");

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        NotificationResponse response = notificationService.toResponse(stored);

        assertThat(response.title()).isEqualTo("Booking rejected");
        assertThat(response.message()).isEqualTo("Khách sạn đã hết phòng ngày này");
    }

    @Test
    void olderNotificationsWithoutKeysKeepTheirText() {
        Notification legacy = notification(13L, alice);
        legacy.setTitle("Booking approved");
        legacy.setMessage("Your booking #3 has been approved");

        NotificationResponse response = notificationService.toResponse(legacy);

        assertThat(response.title()).isEqualTo("Booking approved");
        assertThat(response.message()).isEqualTo("Your booking #3 has been approved");
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
