package com.nhom2.booking.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.auth.AuthRepository
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.domain.isStrongPassword
import com.nhom2.booking.domain.isValidEmail
import com.nhom2.booking.domain.isValidOtp
import com.nhom2.booking.ui.UiEvent
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CODE_USERNAME_EXISTS = "error.username.exists"
private const val CODE_EMAIL_EXISTS = "error.email.exists"
private const val CODE_OTP_INVALID = "error.otp.invalid"
private const val CODE_REGISTRATION_NOT_FOUND = "error.registration.notFound"

enum class RegisterStep { FORM, OTP }

data class RegisterUiState(
    val step: RegisterStep = RegisterStep.FORM,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirm: String = "",
    val otp: String = "",
    val usernameError: UiText? = null,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val confirmError: UiText? = null,
    val otpError: UiText? = null,
    val loading: Boolean = false,
)

class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, usernameError = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null) }

    fun onConfirmChange(value: String) = _state.update { it.copy(confirm = value, confirmError = null) }

    fun onOtpChange(value: String) = _state.update { it.copy(otp = sanitizeOtp(value), otpError = null) }

    fun backToForm() = _state.update { it.copy(step = RegisterStep.FORM, otp = "", otpError = null) }

    fun submitForm() {
        val current = _state.value
        if (current.loading) return
        val withErrors = current.copy(
            usernameError = if (current.username.isBlank()) UiText.Res(R.string.auth_username_required) else null,
            emailError = when {
                current.email.isBlank() -> UiText.Res(R.string.auth_email_required)
                !isValidEmail(current.email) -> UiText.Res(R.string.auth_email_invalid)
                else -> null
            },
            passwordError = when {
                current.password.isEmpty() -> UiText.Res(R.string.auth_password_required)
                !isStrongPassword(current.password) -> UiText.Res(R.string.auth_password_policy)
                else -> null
            },
            confirmError = when {
                current.confirm.isEmpty() -> UiText.Res(R.string.auth_confirm_required)
                current.confirm != current.password -> UiText.Res(R.string.auth_password_mismatch)
                else -> null
            },
        )
        val hasErrors = listOf(
            withErrors.usernameError, withErrors.emailError, withErrors.passwordError, withErrors.confirmError,
        ).any { it != null }
        if (hasErrors) {
            _state.value = withErrors
            return
        }

        _state.update { it.copy(loading = true, username = it.username.trim(), email = it.email.trim()) }
        val submitted = _state.value
        viewModelScope.launch {
            try {
                authRepository.register(submitted.username, submitted.email, submitted.password)
                _state.update { it.copy(step = RegisterStep.OTP, otp = "") }
                _events.send(UiEvent.ShowMessage(UiText.Res(R.string.auth_otp_sent_toast)))
            } catch (e: ApiException) {
                _events.send(UiEvent.ShowMessage(registerErrorText(e)))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun submitOtp() {
        val current = _state.value
        if (current.loading) return
        if (!isValidOtp(current.otp)) {
            _state.update { it.copy(otpError = otpInputError(current.otp)) }
            return
        }

        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                authRepository.verifyRegistration(current.email, current.otp)
                _events.send(UiEvent.ShowMessage(UiText.Res(R.string.auth_register_success)))
                _events.send(UiEvent.Completed)
            } catch (e: ApiException) {
                handleOtpError(e)
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun registerErrorText(e: ApiException): UiText = when ((e as? ApiException.Http)?.code) {
        CODE_USERNAME_EXISTS -> UiText.Res(R.string.auth_username_exists)
        CODE_EMAIL_EXISTS -> UiText.Res(R.string.auth_email_exists)
        else -> e.toUiText(UiText.Res(R.string.error_generic_failed))
    }

    private suspend fun handleOtpError(e: ApiException) {
        val text = when ((e as? ApiException.Http)?.code) {
            CODE_OTP_INVALID -> UiText.Res(R.string.auth_otp_invalid_or_expired)
            // Both mean this sign-up cannot be finished any more, so the user starts over from the form.
            CODE_EMAIL_EXISTS -> UiText.Res(R.string.auth_email_taken_meanwhile).also { backToForm() }
            CODE_REGISTRATION_NOT_FOUND -> UiText.Res(R.string.auth_no_registration).also { backToForm() }
            else -> e.toUiText(UiText.Res(R.string.auth_otp_verify_failed))
        }
        _events.send(UiEvent.ShowMessage(text))
    }
}
