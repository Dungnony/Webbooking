package com.Nhom2.booking.service;
import com.Nhom2.booking.dto.ForgotPasswordRequest;
import com.Nhom2.booking.dto.LoginRequest;
import com.Nhom2.booking.dto.RegisterRequest;
import com.Nhom2.booking.dto.ResetPasswordRequest;
import com.Nhom2.booking.dto.UpdateProfileRequest;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.exception.UnauthorizedException;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.repository.UserRepository;
import com.Nhom2.booking.exception.TooManyRequestsException;
import com.Nhom2.booking.security.GoogleTokenVerifier;
import com.Nhom2.booking.security.JwtUtil;
import com.Nhom2.booking.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String EMAIL_CHANGE_OTP_PREFIX = "email-change:";
    static final String EMAIL_EXISTS = "error.email.exists";
    private static final String OTP_INVALID = "error.otp.invalid";
    private static final String OTP_REQUIRED = "error.otp.required";
    private static final String USERNAME_REQUIRED = "error.username.required";
    private static final String NEW_PASSWORD_REQUIRED = "error.password.required";
    private static final String USER_NOT_FOUND = "error.user.notFound";
    private static final String ACCOUNT_NOT_FOUND = "error.account.notFound";
    private static final String INVALID_CREDENTIALS = "error.login.invalidCredentials";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final String PASSWORD_POLICY = "error.password.policy";
    // Brute-force protection: limits are per account/email, see RateLimiter for why not per IP.
    static final int MAX_FAILED_LOGINS = 5;
    static final int MAX_OTP_REQUESTS = 3;
    private static final Duration LIMIT_WINDOW = Duration.ofMinutes(15);
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final MailService mailService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final RateLimiter rateLimiter;
    private final PendingRegistrations pendingRegistrations;
    private final Messages messages;

    public UserService(
            UserRepository userRepository,
            JwtUtil jwtUtil,
            MailService mailService,
            OtpService otpService,
            PasswordEncoder passwordEncoder,
            GoogleTokenVerifier googleTokenVerifier,
            RateLimiter rateLimiter,
            PendingRegistrations pendingRegistrations,
            Messages messages
    ) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.mailService = mailService;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.googleTokenVerifier = googleTokenVerifier;
        this.rateLimiter = rateLimiter;
        this.pendingRegistrations = pendingRegistrations;
        this.messages = messages;
    }
    // GET ALL
    public List<User> getAll() {
        return userRepository.findAll();
    }
    // GET ONE
    public Optional<User> getUser(Long id) {
        return userRepository.findById(id);
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
    }

    /**
     * Self-service password change. Username is the JWT subject and role is admin-managed,
     * so neither can be changed here; the email goes through requestEmailChange/confirmEmailChange.
     */
    public User updateProfile(String username, UpdateProfileRequest request) {
        if (request == null || isBlank(request.getNewPassword())) {
            throw new BusinessException(NEW_PASSWORD_REQUIRED);
        }

        User user = getByUsername(username);
        validatePasswordStrength(request.getNewPassword(), user.getUsername(), user.getEmail());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        return userRepository.save(user);
    }

    /** {@code avatarUrl} comes from ImageStorageService after the upload succeeded. */
    public User setAvatarUrl(String username, String avatarUrl) {
        User user = getByUsername(username);
        user.setAvatarUrl(avatarUrl);
        return userRepository.save(user);
    }

    /**
     * Bước 1 đổi email: gửi OTP tới email MỚI. Email chỉ được đổi sau khi xác nhận OTP,
     * vì Google login gắn tài khoản theo email nên email phải chứng minh được quyền sở hữu.
     */
    public String requestEmailChange(String username, String newEmail) {
        String email = requireValidEmail(newEmail);
        User user = getByUsername(username);
        if (email.equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException("error.email.sameAsCurrent");
        }
        ensureEmailNotUsedByOthers(email, user);
        limitOtpRequests("otp-email:" + username);

        String otp = otpService.generateOtp(emailChangeOtpKey(username, email));
        mailService.sendMail(
                email,
                messages.get("mail.emailChangeOtp.subject"),
                messages.get("mail.emailChangeOtp.body", user.getUsername(), otp, OtpService.OTP_TTL.toMinutes()));
        return maskEmail(email);
    }

    /**
     * Bước 2 đổi email: OTP gắn với đúng cặp (username, email mới) nên không dùng được cho email khác.
     */
    public User confirmEmailChange(String username, String newEmail, String otp) {
        String email = requireValidEmail(newEmail);
        if (isBlank(otp)) {
            throw new BusinessException(OTP_REQUIRED);
        }

        User user = getByUsername(username);
        String otpKey = emailChangeOtpKey(username, email);
        if (!otpService.verifyOtp(otpKey, otp.trim())) {
            throw new BusinessException(OTP_INVALID);
        }
        // Re-checked because another account may have claimed the address since the OTP was sent.
        ensureEmailNotUsedByOthers(email, user);

        user.setEmail(email);
        User saved = userRepository.save(user);
        otpService.removeOtp(otpKey);
        return saved;
    }

    private String requireValidEmail(String email) {
        if (isBlank(email) || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new BusinessException("error.email.invalid");
        }
        return email.trim();
    }

    private void ensureEmailNotUsedByOthers(String email, User user) {
        if (isEmailTakenByOther(email, user.getId())) {
            throw new BusinessException(EMAIL_EXISTS);
        }
    }

    /** One email belongs to at most one account; {@code ownerId} is null for an account not created yet. */
    private boolean isEmailTakenByOther(String email, Long ownerId) {
        return userRepository.findByEmailIgnoreCase(email).stream()
                .anyMatch(other -> !other.getId().equals(ownerId));
    }

    // Each OTP request sends a mail from the team's Gmail account, so it is capped per account/email.
    private void limitOtpRequests(String key) {
        if (!rateLimiter.tryAcquire(key, MAX_OTP_REQUESTS, LIMIT_WINDOW)) {
            throw new TooManyRequestsException("error.otp.tooManyRequests");
        }
    }

    private static String emailChangeOtpKey(String username, String email) {
        return EMAIL_CHANGE_OTP_PREFIX + username + ":" + email;
    }
    // CREATE / UPDATE
    public User create(User user) {
        User existingUser = null;
        if (user.getId() != null) {
            existingUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
        }
        if (user.getRole() == null) {
            user.setRole(existingUser != null && existingUser.getRole() != null
                    ? existingUser.getRole()
                    : UserRole.USER);
        }
        if (!isBlank(user.getEmail()) && isEmailTakenByOther(user.getEmail().trim(), user.getId())) {
            throw new BusinessException(EMAIL_EXISTS);
        }
        if (isBlank(user.getPassword())) {
            if (existingUser == null) {
                throw new BusinessException(PASSWORD_POLICY);
            }
            user.setPassword(existingUser.getPassword());
        } else if (!isBcrypt(user.getPassword())) {
            validatePasswordStrength(user.getPassword(), user.getUsername(), user.getEmail());
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return userRepository.save(user);
    }
    // DELETE
    public void delete(Long id) {
        userRepository.deleteById(id);
    }
    //sendotp
    public String sendOtp(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException("error.username.exists");
        }
        String email = requireValidEmail(request.getEmail());
        if (isEmailTakenByOther(email, null)) {
            throw new BusinessException(EMAIL_EXISTS);
        }
        limitOtpRequests("otp-register:" + email.toLowerCase(Locale.ROOT));
        pendingRegistrations.put(new PendingRegistrations.Registration(
                request.getUsername(), email, passwordEncoder.encode(request.getPassword())));
        String otp = otpService.generateOtp(email);
        mailService.sendMail(
                email,
                messages.get("mail.registerOtp.subject"),
                messages.get("mail.registerOtp.body", otp, OtpService.OTP_TTL.toMinutes()));
        return "OTP sent to email";
    }
    //verify otp
    public String verifyOtp(String rawEmail, String otp) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (!otpService.verifyOtp(email, otp)) {
            throw new BusinessException(OTP_INVALID);
        }
        PendingRegistrations.Registration registration = pendingRegistrations.take(email)
                .orElseThrow(() -> new BusinessException("error.registration.notFound"));
        // Re-checked because Google login or an email change may have claimed the address since the OTP was sent.
        if (isEmailTakenByOther(email, null)) {
            throw new BusinessException(EMAIL_EXISTS);
        }
        User user = new User();
        user.setUsername(registration.username());
        user.setPassword(registration.passwordHash());
        user.setEmail(registration.email());
        user.setRole(UserRole.USER);
        userRepository.save(user);
        otpService.removeOtp(email);
        return "Register success";
    }
    // LOGIN
    public String login(LoginRequest request) {
        String attemptKey = "login:" + String.valueOf(request.getUsername()).trim().toLowerCase(Locale.ROOT);
        if (rateLimiter.isLimited(attemptKey, MAX_FAILED_LOGINS)) {
            throw new TooManyRequestsException("error.login.locked");
        }

        Optional<User> found = userRepository.findByUsername(request.getUsername());
        // Same error for an unknown user and a wrong password, so the response does not reveal which usernames exist.
        if (found.isEmpty()) {
            rateLimiter.record(attemptKey, LIMIT_WINDOW);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (user.getRole() == null) {
            user.setRole(UserRole.USER);
            userRepository.save(user);
        }
        if (!passwordMatches(request.getPassword(), user.getPassword())) {
            rateLimiter.record(attemptKey, LIMIT_WINDOW);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        rateLimiter.reset(attemptKey);
        if (!isBcrypt(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);
        }
        sendNoticeMail(
                user.getEmail(),
                messages.get("mail.loginNotice.subject"),
                messages.get("mail.loginNotice.body", user.getUsername()));
        return jwtUtil.generateToken(user.getUsername(), user.getRole());
    }

    // ═══════════════════════════════════════════════════════════
    // FORGOT PASSWORD (tìm theo username)
    // ═══════════════════════════════════════════════════════════

    /**
     * Bước 1: User nhập username → tìm user → gửi OTP đến email liên kết.
     * Trả về email đã mask (vd: d***d@gmail.com) để hiển thị cho user.
     */
    public String forgotPassword(ForgotPasswordRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException(USERNAME_REQUIRED);
        }
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new BusinessException(ACCOUNT_NOT_FOUND));

        String email = user.getEmail();
        limitOtpRequests("otp-reset:" + user.getUsername());
        String otp = otpService.generateOtp("reset:" + user.getUsername());

        mailService.sendMail(
                email,
                messages.get("mail.passwordResetOtp.subject"),
                messages.get("mail.passwordResetOtp.body", user.getUsername(), otp, OtpService.OTP_TTL.toMinutes()));

        return maskEmail(email);
    }

    /**
     * Bước 2: User nhập OTP + mật khẩu mới → verify OTP → cập nhật mật khẩu.
     */
    public String resetPassword(ResetPasswordRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException(USERNAME_REQUIRED);
        }
        if (request.getOtp() == null || request.getOtp().trim().isEmpty()) {
            throw new BusinessException(OTP_REQUIRED);
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new BusinessException(NEW_PASSWORD_REQUIRED);
        }

        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new BusinessException(ACCOUNT_NOT_FOUND));

        String otpKey = "reset:" + user.getUsername();
        if (!otpService.verifyOtp(otpKey, request.getOtp().trim())) {
            throw new BusinessException(OTP_INVALID);
        }

        // Validate password strength
        validatePasswordStrength(request.getNewPassword(), user.getUsername(), user.getEmail());

        // Cập nhật mật khẩu
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        otpService.removeOtp(otpKey);

        // Gửi mail thông báo
        mailService.sendMail(
                user.getEmail(),
                messages.get("mail.passwordChanged.subject"),
                messages.get("mail.passwordChanged.body", user.getUsername()));

        return "Đặt lại mật khẩu thành công";
    }

    /**
     * Mask email: duytuvvd@gmail.com → d******d@gmail.com
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return email.charAt(0) + "***" + email.substring(atIndex);
        }
        return email.charAt(0)
                + "*".repeat(atIndex - 2)
                + email.charAt(atIndex - 1)
                + email.substring(atIndex);
    }

    // ═══════════════════════════════════════════════════════════
    // GOOGLE LOGIN
    // ═══════════════════════════════════════════════════════════

    /**
     * Đăng nhập bằng Google ID Token.
     * - Verify token với Google
     * - Nếu email đã có → login user đó
     * - Nếu chưa có → tạo user mới (username = email prefix, password random)
     * - Trả về JWT
     */
    public String loginWithGoogle(String googleIdToken) {
        String email = googleTokenVerifier.verifyEmail(googleIdToken);

        List<User> existingUsers = userRepository.findByEmailIgnoreCase(email);
        // Accounts created before emails were enforced unique may share one; picking either could sign the user
        // into someone else's account.
        if (existingUsers.size() > 1) {
            throw new UnauthorizedException("error.google.multipleAccounts");
        }

        User user;
        if (!existingUsers.isEmpty()) {
            user = existingUsers.get(0);
            if (user.getRole() == null) {
                user.setRole(UserRole.USER);
                userRepository.save(user);
            }
        } else {
            // Tạo user mới
            user = new User();
            String baseUsername = email.split("@")[0];
            String username = baseUsername;
            int counter = 1;
            while (userRepository.findByUsername(username).isPresent()) {
                username = baseUsername + counter;
                counter++;
            }
            user.setUsername(username);
            user.setEmail(email);
            // Password ngẫu nhiên (user chỉ login qua Google)
            String randomPassword = generateRandomPassword();
            user.setPassword(passwordEncoder.encode(randomPassword));
            user.setRole(UserRole.USER);
            userRepository.save(user);
        }

        sendNoticeMail(
                user.getEmail(),
                messages.get("mail.googleLoginNotice.subject"),
                messages.get("mail.googleLoginNotice.body", user.getUsername()));

        return jwtUtil.generateToken(user.getUsername(), user.getRole());
    }

    /**
     * Informational mail only: an SMTP outage must not block login. OTP mails do not use this,
     * since the flow cannot continue without them.
     */
    private void sendNoticeMail(String to, String subject, String text) {
        try {
            mailService.sendMail(to, subject, text);
        } catch (MailException e) {
            log.warn("Could not send notice mail '{}': {}", subject, e.getMessage());
        }
    }

    /**
     * Sinh password ngẫu nhiên cho Google user (16 ký tự).
     */
    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (isBcrypt(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return storedPassword != null && storedPassword.equals(rawPassword);
    }
    private boolean isBcrypt(String password) {
        return password != null && password.startsWith("$2");
    }
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
    private void validatePasswordStrength(String password, String username, String email) {
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(PASSWORD_POLICY);
        }
        if (password.contains(" ")) {
            throw new BusinessException(PASSWORD_POLICY);
        }
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new BusinessException(PASSWORD_POLICY);
        }
    }
}
