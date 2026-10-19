package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.LoginRequest;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.repository.UserRepository;
import com.Nhom2.booking.security.GoogleTokenVerifier;
import com.Nhom2.booking.exception.TooManyRequestsException;
import com.Nhom2.booking.exception.UnauthorizedException;
import com.Nhom2.booking.security.JwtUtil;
import com.Nhom2.booking.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceLoginTest {

    private static final String GOOGLE_TOKEN = "google-id-token";

    private UserRepository userRepository;
    private JwtUtil jwtUtil;
    private MailService mailService;
    private PasswordEncoder passwordEncoder;
    private GoogleTokenVerifier googleTokenVerifier;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtUtil = mock(JwtUtil.class);
        mailService = mock(MailService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        googleTokenVerifier = mock(GoogleTokenVerifier.class);
        userService = new UserService(
                userRepository, jwtUtil, mailService, mock(OtpService.class), passwordEncoder, googleTokenVerifier,
                new RateLimiter(Clock.systemUTC()));

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken(anyString(), any(UserRole.class)))
                .thenAnswer(invocation -> "jwt-for-" + invocation.getArgument(0));
    }

    @Test
    void googleLoginSignsIntoTheAccountOwningTheEmail() {
        when(googleTokenVerifier.verifyEmail(GOOGLE_TOKEN)).thenReturn("alice@gmail.com");
        when(userRepository.findByEmailIgnoreCase("alice@gmail.com")).thenReturn(List.of(user("alice", "alice@gmail.com")));

        assertThat(userService.loginWithGoogle(GOOGLE_TOKEN)).isEqualTo("jwt-for-alice");
    }

    @Test
    void googleLoginCreatesAnAccountForANewEmail() {
        when(googleTokenVerifier.verifyEmail(GOOGLE_TOKEN)).thenReturn("carol@gmail.com");
        when(userRepository.findByEmailIgnoreCase("carol@gmail.com")).thenReturn(List.of());
        when(userRepository.findByUsername("carol")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$random");

        String token = userService.loginWithGoogle(GOOGLE_TOKEN);

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(created.capture());
        assertThat(created.getValue().getUsername()).isEqualTo("carol");
        assertThat(created.getValue().getEmail()).isEqualTo("carol@gmail.com");
        assertThat(created.getValue().getRole()).isEqualTo(UserRole.USER);
        assertThat(token).isEqualTo("jwt-for-carol");
    }

    @Test
    void googleLoginRefusesToGuessWhenSeveralAccountsShareTheEmail() {
        when(googleTokenVerifier.verifyEmail(GOOGLE_TOKEN)).thenReturn("shared@gmail.com");
        when(userRepository.findByEmailIgnoreCase("shared@gmail.com"))
                .thenReturn(List.of(user("attacker", "shared@gmail.com"), user("victim", "shared@gmail.com")));

        assertThatThrownBy(() -> userService.loginWithGoogle(GOOGLE_TOKEN)).isInstanceOf(RuntimeException.class);
        verify(jwtUtil, never()).generateToken(anyString(), any());
    }

    @Test
    void googleLoginSucceedsEvenWhenTheNotificationMailFails() {
        when(googleTokenVerifier.verifyEmail(GOOGLE_TOKEN)).thenReturn("alice@gmail.com");
        when(userRepository.findByEmailIgnoreCase("alice@gmail.com")).thenReturn(List.of(user("alice", "alice@gmail.com")));
        doThrow(new MailSendException("SMTP down")).when(mailService).sendMail(anyString(), anyString(), anyString());

        assertThat(userService.loginWithGoogle(GOOGLE_TOKEN)).isEqualTo("jwt-for-alice");
    }

    @Test
    void passwordLoginSucceedsEvenWhenTheNotificationMailFails() {
        User alice = user("alice", "alice@gmail.com");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("Secret#2026", "$2a$hash")).thenReturn(true);
        doThrow(new MailSendException("SMTP down")).when(mailService).sendMail(anyString(), anyString(), anyString());

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("Secret#2026");

        assertThat(userService.login(request)).isEqualTo("jwt-for-alice");
    }

    @Test
    void tooManyWrongPasswordsLockTheAccountEvenForTheRightPassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "alice@gmail.com")));
        when(passwordEncoder.matches("Secret#2026", "$2a$hash")).thenReturn(true);

        for (int i = 0; i < UserService.MAX_FAILED_LOGINS; i++) {
            assertThatThrownBy(() -> userService.login(loginRequest("alice", "wrong")))
                    .isNotInstanceOf(TooManyRequestsException.class);
        }

        assertThatThrownBy(() -> userService.login(loginRequest("alice", "Secret#2026")))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void unknownUserAndWrongPasswordGetTheSameUnauthorizedError() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "alice@gmail.com")));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(loginRequest("alice", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("code", "error.login.invalidCredentials");
        assertThatThrownBy(() -> userService.login(loginRequest("ghost", "whatever")))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("code", "error.login.invalidCredentials");
    }

    @Test
    void successfulLoginResetsTheFailureCount() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "alice@gmail.com")));
        when(passwordEncoder.matches("Secret#2026", "$2a$hash")).thenReturn(true);

        for (int i = 0; i < UserService.MAX_FAILED_LOGINS - 1; i++) {
            assertThatThrownBy(() -> userService.login(loginRequest("alice", "wrong")));
        }
        userService.login(loginRequest("alice", "Secret#2026"));
        assertThatThrownBy(() -> userService.login(loginRequest("alice", "wrong")))
                .isNotInstanceOf(TooManyRequestsException.class);

        assertThat(userService.login(loginRequest("alice", "Secret#2026"))).isEqualTo("jwt-for-alice");
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static User user(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("$2a$hash");
        user.setRole(UserRole.USER);
        return user;
    }
}
