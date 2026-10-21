package com.nhom2.booking.data.auth

import com.nhom2.booking.data.model.GoogleLoginBody
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.data.session.Session
import com.nhom2.booking.data.session.SessionStore

/** All calls throw [ApiException] on failure. */
class AuthRepository(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
) {
    suspend fun login(username: String, password: String): Session =
        saveToken(apiCall { api.login(LoginRequest(username, password)) })

    suspend fun loginWithGoogle(idToken: String): Session =
        saveToken(apiCall { api.loginWithGoogle(GoogleLoginBody(idToken)) })

    private suspend fun saveToken(token: String): Session =
        sessionStore.save(token.trim())
            ?: throw ApiException.UnexpectedResponse("Login returned an unreadable token")

    /** Step 1 of sign-up: the backend mails an OTP to [email]. */
    suspend fun register(username: String, email: String, password: String) {
        apiCall { api.register(RegisterRequest(username, password, email)) }
    }

    /** Step 2 of sign-up: creates the account. The user still has to sign in afterwards. */
    suspend fun verifyRegistration(email: String, otp: String) {
        apiCall { api.verifyOtp(VerifyOtpRequest(email, otp)) }
    }

    /** Returns the masked email the reset code was sent to. */
    suspend fun requestPasswordReset(username: String): String =
        apiCall { api.forgotPassword(ForgotPasswordRequest(username)) }.trim()

    suspend fun resetPassword(username: String, otp: String, newPassword: String) {
        apiCall { api.resetPassword(ResetPasswordRequest(username, otp, newPassword)) }
    }

    suspend fun logout() {
        sessionStore.clear()
    }
}
