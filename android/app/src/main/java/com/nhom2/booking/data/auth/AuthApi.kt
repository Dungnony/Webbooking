package com.nhom2.booking.data.auth

import com.nhom2.booking.data.model.GoogleLoginBody
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** Mirrors AuthController on the backend. Every endpoint answers with a plain string. */
interface AuthApi {
    /** Returns the JWT. */
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): String

    /** Exchanges a Google ID token for the app's JWT; creates the account on first use. */
    @POST("auth/google")
    suspend fun loginWithGoogle(@Body body: GoogleLoginBody): String

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): String

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): String

    /** Returns the masked email the code was sent to, e.g. `d******d@gmail.com`. */
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): String

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): String
}

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(val username: String, val password: String, val email: String)

@Serializable
data class VerifyOtpRequest(val email: String, val otp: String)

@Serializable
data class ForgotPasswordRequest(val username: String)

@Serializable
data class ResetPasswordRequest(val username: String, val otp: String, val newPassword: String)
