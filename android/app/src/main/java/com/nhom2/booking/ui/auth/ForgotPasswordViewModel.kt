package com.nhom2.booking.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.auth.AuthRepository
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.domain.isStrongPassword
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

enum class ForgotPasswordStep { USERNAME, RESET, DONE }

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.USERNAME,
    val username: String = "",
    val maskedEmail: String = "",
    val otp: String = "",
    val password: String = "",
    val confirm: String = "",
    val usernameError: UiText? = null,
    val otpError: UiText? = null,
    val passwordError: UiText? = null,
    val confirmError: UiText? = null,
    val loading: Boolean = false,
)

class ForgotPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, usernameError = null) }

    fun onOtpChange(value: String) = _state.update { it.copy(otp = sanitizeOtp(value), otpError = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null) }

    fun onConfirmChange(value: String) = _state.update { it.copy(confirm = value, confirmError = null) }

    fun backToUsername() = _state.update {
        it.copy(step = ForgotPasswordStep.USERNAME, otp = "", password = "", confirm = "")
    }

    fun requestCode() {
        val current = _state.value
        if (current.loading) return
        if (current.username.isBlank()) {
            _state.update { it.copy(usernameError = UiText.Res(R.string.auth_username_required)) }
            return
        }

        val username = current.username.trim()
        _state.update { it.copy(loading = true, username = username) }
        viewModelScope.launch {
            try {
                val maskedEmail = authRepository.requestPasswordReset(username)
                _state.update { it.copy(step = ForgotPasswordStep.RESET, maskedEmail = maskedEmail) }
                _events.send(UiEvent.ShowMessage(UiText.Res(R.string.forgot_code_sent)))
            } catch (e: ApiException) {
                _events.send(UiEvent.ShowMessage(e.toUiText(UiText.Res(R.string.forgot_username_not_found))))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun resetPassword() {
        val current = _state.value
        if (current.loading) return
        val withErrors = current.copy(
            otpError = if (isValidOtp(current.otp)) null else otpInputError(current.otp),
            passwordError = when {
                current.password.isEmpty() -> UiText.Res(R.string.forgot_new_password_required)
                !isStrongPassword(current.password) -> UiText.Res(R.string.auth_password_policy)
                else -> null
            },
            confirmError = when {
                current.confirm.isEmpty() -> UiText.Res(R.string.auth_confirm_required)
                current.confirm != current.password -> UiText.Res(R.string.auth_password_mismatch)
                else -> null
            },
        )
        if (withErrors.otpError != null || withErrors.passwordError != null || withErrors.confirmError != null) {
            _state.value = withErrors
            return
        }

        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                authRepository.resetPassword(current.username, current.otp, current.password)
                _state.update { it.copy(step = ForgotPasswordStep.DONE, otp = "", password = "", confirm = "") }
            } catch (e: ApiException) {
                _events.send(UiEvent.ShowMessage(e.toUiText(UiText.Res(R.string.forgot_reset_failed))))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
