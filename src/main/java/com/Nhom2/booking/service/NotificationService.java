package com.Nhom2.booking.service;

import com.Nhom2.booking.config.LocaleConfig;
import com.Nhom2.booking.dto.NotificationResponse;
import com.Nhom2.booking.entity.Notification;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.repository.NotificationRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Messages messages;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            Messages messages
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.messages = messages;
    }

    /**
     * System notification: title and message are message codes, rendered in the reader's language when listed.
     * Arguments are stored as text, so callers format numbers (amounts) themselves.
     */
    public Notification create(User user, String titleKey, String messageKey, Object... args) {
        List<String> storedArgs = Arrays.stream(args).map(String::valueOf).toList();
        Notification notification = newNotification(user, titleKey);
        notification.setMessageKey(messageKey);
        notification.setMessageArgs(storedArgs);
        notification.setMessage(messages.forLocale(LocaleConfig.DEFAULT_LOCALE, messageKey, storedArgs.toArray()));
        return notificationRepository.save(notification);
    }

    /** Notification whose message an admin wrote; only the title is translated. */
    public Notification createWithMessage(User user, String titleKey, String message) {
        Notification notification = newNotification(user, titleKey);
        notification.setMessage(message);
        return notificationRepository.save(notification);
    }

    public void notifyAdmins(String titleKey, String messageKey, Object... args) {
        for (User admin : userRepository.findByRole(UserRole.ADMIN)) {
            create(admin, titleKey, messageKey, args);
        }
    }

    public List<NotificationResponse> getMyNotifications() {
        User user = getCurrentUser();
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    public NotificationResponse markAsRead(Long notificationId) {
        Notification notification = getOwnNotification(notificationId);
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    public void delete(Long notificationId) {
        notificationRepository.delete(getOwnNotification(notificationId));
    }

    NotificationResponse toResponse(Notification notification) {
        String title = notification.getTitleKey() == null
                ? notification.getTitle()
                : messages.get(notification.getTitleKey());
        String message = notification.getMessageKey() == null
                ? notification.getMessage()
                : messages.get(notification.getMessageKey(), argsOf(notification));
        return new NotificationResponse(
                notification.getId(), title, message, notification.getRead(), notification.getCreatedAt());
    }

    private Notification newNotification(User user, String titleKey) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitleKey(titleKey);
        notification.setTitle(messages.forLocale(LocaleConfig.DEFAULT_LOCALE, titleKey));
        notification.setRead(false);
        return notification;
    }

    private static Object[] argsOf(Notification notification) {
        return notification.getMessageArgs() == null ? new Object[0] : notification.getMessageArgs().toArray();
    }

    private Notification getOwnNotification(Long notificationId) {
        User user = getCurrentUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("error.notification.notFound"));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("error.notification.notOwner");
        }
        return notification;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("error.user.notFound"));
    }
}
