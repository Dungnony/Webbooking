package com.Nhom2.booking.service;
import com.Nhom2.booking.dto.ForgotPasswordRequest;
import com.Nhom2.booking.dto.LoginRequest;
import com.Nhom2.booking.dto.RegisterRequest;
import com.Nhom2.booking.dto.ResetPasswordRequest;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.repository.UserRepository;
import com.Nhom2.booking.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class UserService {
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final String PASSWORD_POLICY_MESSAGE =
            "Mật khẩu phải chứ từ 8-72 ký tự bào gồm chữ hoa, chữ thường, số , ký tự đặc biệt và không có khoảng trống.";
    private final Map<String, RegisterRequest> pendingUsers = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final MailService mailService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    @Value("${google.client-id:}")
    private String googleClientId;

    public UserService(
            UserRepository userRepository,
            JwtUtil jwtUtil,
            MailService mailService,
            OtpService otpService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.mailService = mailService;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
    }
    // GET ALL
    public List<User> getAll() {
        return userRepository.findAll();
    }
    // GET ONE
    public Optional<User> getUser(Long id) {
        return userRepository.findById(id);
    }
    // CREATE / UPDATE
    public User create(User user) {
        User existingUser = null;
        if (user.getId() != null) {
            existingUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
        }
        if (user.getRole() == null) {
            user.setRole(existingUser != null && existingUser.getRole() != null
                    ? existingUser.getRole()
                    : UserRole.USER);
        }
        if (isBlank(user.getPassword())) {
            if (existingUser == null) {
                throw new RuntimeException(PASSWORD_POLICY_MESSAGE);
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
            throw new RuntimeException("Username already exists");
        }
        String otp = otpService.generateOtp(request.getEmail());
        pendingUsers.put(request.getEmail(), request);
        mailService.sendMail(
                request.getEmail(),
                "Xác nhận đăng ký tài khoản",
                "Mã OTP của bạn là: " + otp
        );
        return "OTP sent to email";
    }
    //verify otp
    public String verifyOtp(String email, String otp) {
        if (!otpService.verifyOtp(email, otp)) {
            throw new RuntimeException("OTP invalid");
        }
        RegisterRequest request = pendingUsers.get(email);
        if (request == null) {
            throw new RuntimeException("No registration request found");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole(UserRole.USER);
        userRepository.save(user);
        otpService.removeOtp(email);
        pendingUsers.remove(email);
        return "Register success";
    }
    // LOGIN
    public String login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() == null) {
            user.setRole(UserRole.USER);
            userRepository.save(user);
        }
        if (!passwordMatches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Wrong password");
        }
        if (!isBcrypt(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);
        }
        // gửi mail đăng nhập
        mailService.sendMail(
                user.getEmail(),
                "Đăng nhập thành công",
                "Xin chào " + user.getUsername() + ", bạn vừa đăng nhập vào hệ thống."
        );
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
            throw new RuntimeException("Vui lòng nhập tên đăng nhập");
        }
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với username: " + request.getUsername()));

        String email = user.getEmail();
        String otp = otpService.generateOtp("reset:" + user.getUsername());

        mailService.sendMail(
                email,
                "Đặt lại mật khẩu - Booking.com",
                "Xin chào " + user.getUsername() + ",\n\n"
                        + "Mã OTP để đặt lại mật khẩu của bạn là: " + otp + "\n\n"
                        + "Mã này có hiệu lực trong vòng 5 phút.\n"
                        + "Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này."
        );

        return maskEmail(email);
    }

    /**
     * Bước 2: User nhập OTP + mật khẩu mới → verify OTP → cập nhật mật khẩu.
     */
    public String resetPassword(ResetPasswordRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Vui lòng nhập tên đăng nhập");
        }
        if (request.getOtp() == null || request.getOtp().trim().isEmpty()) {
            throw new RuntimeException("Vui lòng nhập mã OTP");
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new RuntimeException("Vui lòng nhập mật khẩu mới");
        }

        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        String otpKey = "reset:" + user.getUsername();
        if (!otpService.verifyOtp(otpKey, request.getOtp().trim())) {
            throw new RuntimeException("Mã OTP không đúng hoặc đã hết hạn");
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
                "Mật khẩu đã được thay đổi - Booking.com",
                "Xin chào " + user.getUsername() + ",\n\n"
                        + "Mật khẩu của bạn đã được thay đổi thành công.\n"
                        + "Nếu bạn không thực hiện thay đổi này, vui lòng liên hệ hỗ trợ ngay."
        );

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
        // Decode & verify Google ID token
        Map<String, String> googleUser = verifyGoogleToken(googleIdToken);
        String email = googleUser.get("email");
        String name = googleUser.get("name");

        if (email == null || email.isEmpty()) {
            throw new RuntimeException("Không thể lấy email từ tài khoản Google");
        }

        // Tìm user theo email
        List<User> existingUsers = userRepository.findByEmail(email);

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

        // Gửi mail thông báo
        mailService.sendMail(
                user.getEmail(),
                "Đăng nhập bằng Google thành công",
                "Xin chào " + user.getUsername() + ", bạn vừa đăng nhập vào hệ thống bằng Google."
        );

        return jwtUtil.generateToken(user.getUsername(), user.getRole());
    }

    /**
     * Verify Google ID Token bằng cách decode JWT payload.
     * Trong production, nên dùng Google API Client Library để verify signature.
     */
    private Map<String, String> verifyGoogleToken(String idToken) {
        try {
            // Decode JWT payload (phần 2 của token, base64)
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new RuntimeException("Token Google không hợp lệ");
            }
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

            // Parse JSON đơn giản
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = mapper.readValue(payload, Map.class);

            String email = (String) claims.get("email");
            String name = (String) claims.get("name");
            Boolean emailVerified = claims.get("email_verified") instanceof Boolean
                    ? (Boolean) claims.get("email_verified") : Boolean.TRUE;

            if (email == null || !emailVerified) {
                throw new RuntimeException("Email Google chưa được xác thực");
            }

            // Verify audience (client ID) nếu đã cấu hình
            if (googleClientId != null && !googleClientId.isEmpty()) {
                String aud = (String) claims.get("aud");
                if (!googleClientId.equals(aud)) {
                    throw new RuntimeException("Google Client ID không khớp");
                }
            }

            Map<String, String> result = new HashMap<>();
            result.put("email", email);
            result.put("name", name != null ? name : email.split("@")[0]);
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Không thể xác thực token Google: " + e.getMessage());
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
            throw new RuntimeException(PASSWORD_POLICY_MESSAGE);
        }
        if (password.contains(" ")) {
            throw new RuntimeException(PASSWORD_POLICY_MESSAGE);
        }
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new RuntimeException(PASSWORD_POLICY_MESSAGE);
        }
    }
}
