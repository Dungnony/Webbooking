package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.RegisterRequest;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.repository.UserRepository;
import com.Nhom2.booking.security.GoogleTokenVerifier;
import com.Nhom2.booking.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One email address may belong to at most one account. */
class UserServiceEmailUniquenessTest {

    private static final Pattern OTP_IN_MAIL = Pattern.compile("\\b(\\d{6})\\b");
    private static final String STRONG_PASSWORD = "Secret#2026";

    private UserRepository userRepository;
    private MailService mailService;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        mailService = mock(MailService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(
                userRepository,
                mock(JwtUtil.class),
                mailService,
                new OtpService(),
                passwordEncoder,
                mock(GoogleTokenVerifier.class));

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
    }

    @Test
    void registrationSendsOtpForAnUnusedEmail() {
        userService.sendOtp(register("carol", "carol@example.com"));

        verify(mailService).sendMail(any(), anyString(), anyString());
    }

    @Test
    void registrationIsRejectedWhenTheEmailAlreadyHasAnAccount() {
        when(userRepository.findByEmailIgnoreCase("Alice@Example.com"))
                .thenReturn(List.of(user(1L, "alice", "alice@example.com")));

        assertThatThrownBy(() -> userService.sendOtp(register("alice2", "Alice@Example.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email already exists");
        verify(mailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    void registrationIsRejectedWhenTheEmailIsInvalid() {
        assertThatThrownBy(() -> userService.sendOtp(register("carol", "not-an-email")))
                .isInstanceOf(RuntimeException.class);
        verify(mailService, never()).sendMail(anyString(), anyString(), anyString());
    }

    @Test
    void otpVerificationFailsIfTheEmailWasTakenInTheMeantime() {
        userService.sendOtp(register("carol", "carol@example.com"));
        String otp = mailedOtp();
        when(userRepository.findByEmailIgnoreCase("carol@example.com"))
                .thenReturn(List.of(user(2L, "google-carol", "carol@example.com")));

        assertThatThrownBy(() -> userService.verifyOtp("carol@example.com", otp))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    void otpVerificationCreatesTheAccount() {
        userService.sendOtp(register("carol", "carol@example.com"));

        assertThat(userService.verifyOtp("carol@example.com", mailedOtp())).isEqualTo("Register success");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void adminCannotCreateAUserWithAnEmailThatIsTaken() {
        when(userRepository.findByEmailIgnoreCase("alice@example.com"))
                .thenReturn(List.of(user(1L, "alice", "alice@example.com")));
        User newUser = user(null, "bob", "alice@example.com");
        newUser.setPassword(STRONG_PASSWORD);

        assertThatThrownBy(() -> userService.create(newUser)).isInstanceOf(RuntimeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updatingAUserMayKeepItsOwnEmail() {
        User alice = user(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(List.of(alice));

        User update = user(1L, "alice", "alice@example.com");
        update.setPassword(null);

        assertThat(userService.create(update).getEmail()).isEqualTo("alice@example.com");
    }

    private String mailedOtp() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMail(anyString(), anyString(), body.capture());
        Matcher matcher = OTP_IN_MAIL.matcher(body.getValue());
        assertThat(matcher.find()).as("mail body contains a 6-digit OTP").isTrue();
        return matcher.group(1);
    }

    private static RegisterRequest register(String username, String email) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(STRONG_PASSWORD);
        return request;
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
