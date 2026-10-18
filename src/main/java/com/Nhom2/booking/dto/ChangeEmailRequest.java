package com.Nhom2.booking.dto;

/**
 * Step 1 sends only {@code email}; step 2 sends the same {@code email} plus the mailed {@code otp}.
 */
public class ChangeEmailRequest {
    private String email;
    private String otp;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
