package com.Nhom2.booking.dto;

/**
 * Self-service password change. The email is changed through the OTP flow
 * ({@link ChangeEmailRequest}) because it must be verified first.
 */
public class UpdateProfileRequest {
    private String newPassword;

    public UpdateProfileRequest() {}

    public UpdateProfileRequest(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
