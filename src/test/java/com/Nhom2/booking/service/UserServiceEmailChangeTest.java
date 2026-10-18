package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.repository.UserRepository;
import com.Nhom2.booking.security.GoogleTokenVerifier;
import com.Nhom2.booking.security.JwtUtil;
import com.Nhom2.booking.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Changing the account email must prove ownership of the new address, otherwise anyone could
 * claim a victim's email and capture the victim's first Google sign-in.
 */
class UserServiceEmailChangeTest {

    private static final Pattern OTP_IN_MAIL = Pattern.compile("\\b(\\d{6})\\b");

    private UserRepository userRepository;
    private MailService mailService;
    private UserService userService;
    private User alice;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        mailService = mock(MailService.class);
        userService = new UserService(
                userRepository,
                mock(JwtUtil.class),
                mailService,
                new OtpService(),
                mock(PasswordEncoder.class),
                mock(GoogleTokenVerifier.class),
                new RateLimiter(Clock.systemUTC()));

        alice = user(5L, "alice", "old@example.com");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestSendsOtpToTheNewAddressWithoutChangingTheEmail() {
        userService.requestEmailChange("alice", " new@example.com ");

        verify(mailService).sendMail(eq("new@example.com"), anyString(), anyString());
        assertThat(alice.getEmail()).isEqualTo("old@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmWithTheMailedOtpUpdatesTheEmail() {
        userService.requestEmailChange("alice", "new@example.com");

        User saved = userService.confirmEmailChange("alice", "new@example.com", mailedOtp());

        assertThat(saved.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void otpCannotBeReusedAfterASuccessfulChange() {
        userService.requestEmailChange("alice", "new@example.com");
        String otp = mailedOtp();
        userService.confirmEmailChange("alice", "new@example.com", otp);

        assertThatThrownBy(() -> userService.confirmEmailChange("alice", "new@example.com", otp))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void wrongOtpIsRejected() {
        userService.requestEmailChange("alice", "new@example.com");
        String wrongOtp = mailedOtp().equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> userService.confirmEmailChange("alice", "new@example.com", wrongOtp))
                .isInstanceOf(RuntimeException.class);
        assertThat(alice.getEmail()).isEqualTo("old@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void otpIssuedForOneAddressCannotConfirmAnother() {
        userService.requestEmailChange("alice", "mine@example.com");

        assertThatThrownBy(() -> userService.confirmEmailChange("alice", "victim@example.com", mailedOtp()))
                .isInstanceOf(RuntimeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmWithoutAnyRequestIsRejected() {
        assertThatThrownBy(() -> userService.confirmEmailChange("alice", "new@example.com", "123456"))
                .isInstanceOf(RuntimeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void emailOwnedByAnotherAccountIsRejected() {
        when(userRepository.findByEmailIgnoreCase("taken@example.com")).thenReturn(List.of(user(9L, "bob", "taken@example.com")));

        assertThatThrownBy(() -> userService.requestEmailChange("alice", "taken@example.com"))
                .isInstanceOf(RuntimeException.class);
        verify(mailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    void invalidOrUnchangedEmailIsRejected() {
        assertThatThrownBy(() -> userService.requestEmailChange("alice", "not-an-email"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> userService.requestEmailChange("alice", null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> userService.requestEmailChange("alice", "old@example.com"))
                .isInstanceOf(RuntimeException.class);
        verify(mailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    private String mailedOtp() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMail(anyString(), anyString(), body.capture());
        Matcher matcher = OTP_IN_MAIL.matcher(body.getValue());
        assertThat(matcher.find()).as("mail body contains a 6-digit OTP").isTrue();
        return matcher.group(1);
    }

    private static User user(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("$2a$hash");
        user.setRole(UserRole.USER);
        return user;
    }
}
