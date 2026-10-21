package com.nhom2.booking.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AccountApi
import com.nhom2.booking.data.model.ChangeEmailBody
import com.nhom2.booking.data.model.UpdateProfileBody
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.domain.isStrongPassword
import com.nhom2.booking.domain.isValidEmail
import com.nhom2.booking.domain.isValidOtp
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.auth.OtpTextField
import com.nhom2.booking.ui.auth.otpInputError
import com.nhom2.booking.ui.auth.sanitizeOtp
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.FormTextField
import com.nhom2.booking.ui.components.LoadingButton
import com.nhom2.booking.ui.components.PasswordTextField
import com.nhom2.booking.ui.components.SecondaryButton
import com.nhom2.booking.ui.components.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileSettingsState(
    val currentEmail: String? = null,
    val newEmail: String = "",
    /** Set once a code was sent; the email only changes after that code is confirmed. */
    val pendingEmail: String? = null,
    val maskedEmail: String = "",
    val otp: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val emailError: UiText? = null,
    val otpError: UiText? = null,
    val passwordError: UiText? = null,
    val confirmError: UiText? = null,
    val emailBusy: Boolean = false,
    val passwordBusy: Boolean = false,
)

class ProfileSettingsViewModel(private val accountApi: AccountApi) : EventViewModel() {

    private val _state = MutableStateFlow(ProfileSettingsState())
    val state: StateFlow<ProfileSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val email = apiCall { accountApi.getMe() }.email
                _state.update { it.copy(currentEmail = email) }
            } catch (e: ApiException) {
                showError(e, R.string.profile_load_failed)
            }
        }
    }

    fun onNewEmailChange(value: String) = _state.update { it.copy(newEmail = value, emailError = null) }
    fun onOtpChange(value: String) = _state.update { it.copy(otp = sanitizeOtp(value), otpError = null) }
    fun onNewPasswordChange(value: String) = _state.update { it.copy(newPassword = value, passwordError = null) }
    fun onConfirmPasswordChange(value: String) = _state.update { it.copy(confirmPassword = value, confirmError = null) }

    fun sendEmailCode() {
        val current = _state.value
        if (current.emailBusy) return
        if (!isValidEmail(current.newEmail)) {
            _state.update { it.copy(emailError = UiText.Res(R.string.auth_email_invalid)) }
            return
        }
        val email = current.newEmail.trim()
        _state.update { it.copy(emailBusy = true) }
        viewModelScope.launch {
            try {
                val masked = apiCall { accountApi.requestEmailChange(ChangeEmailBody(email)) }.trim()
                _state.update { it.copy(pendingEmail = email, maskedEmail = masked, otp = "") }
                showMessage(R.string.profile_otp_sent, masked)
            } catch (e: ApiException) {
                showError(e, R.string.profile_otp_send_failed)
            } finally {
                _state.update { it.copy(emailBusy = false) }
            }
        }
    }

    fun confirmEmail() {
        val current = _state.value
        val pending = current.pendingEmail ?: return
        if (current.emailBusy) return
        if (!isValidOtp(current.otp)) {
            _state.update { it.copy(otpError = otpInputError(current.otp)) }
            return
        }
        _state.update { it.copy(emailBusy = true) }
        viewModelScope.launch {
            try {
                val updated = apiCall { accountApi.confirmEmailChange(ChangeEmailBody(pending, current.otp)) }
                _state.update { it.copy(currentEmail = updated.email, pendingEmail = null, newEmail = "", otp = "") }
                showMessage(R.string.profile_email_changed)
            } catch (e: ApiException) {
                showError(e, R.string.profile_email_change_failed)
            } finally {
                _state.update { it.copy(emailBusy = false) }
            }
        }
    }

    fun cancelEmailChange() = _state.update { it.copy(pendingEmail = null, otp = "", otpError = null) }

    fun changePassword() {
        val current = _state.value
        if (current.passwordBusy) return
        val passwordError = when {
            current.newPassword.isEmpty() -> UiText.Res(R.string.forgot_new_password_required)
            !isStrongPassword(current.newPassword) -> UiText.Res(R.string.auth_password_policy)
            else -> null
        }
        val confirmError = if (current.confirmPassword != current.newPassword) UiText.Res(R.string.auth_password_mismatch) else null
        if (passwordError != null || confirmError != null) {
            _state.update { it.copy(passwordError = passwordError, confirmError = confirmError) }
            return
        }
        _state.update { it.copy(passwordBusy = true) }
        viewModelScope.launch {
            try {
                apiCall { accountApi.changePassword(UpdateProfileBody(current.newPassword)) }
                _state.update { it.copy(newPassword = "", confirmPassword = "") }
                showMessage(R.string.profile_password_changed)
            } catch (e: ApiException) {
                showError(e, R.string.profile_password_change_failed)
            } finally {
                _state.update { it.copy(passwordBusy = false) }
            }
        }
    }
}

@Composable
fun ProfileSettingsScreen(viewModel: ProfileSettingsViewModel, onMessage: (UiText) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(topBar = { AppTopBar(stringResource(R.string.profile_settings_title), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard {
                Text(stringResource(R.string.profile_email_section), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    stringResource(R.string.profile_current_email, state.currentEmail ?: stringResource(R.string.profile_no_email)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.pendingEmail == null) {
                    FormTextField(
                        value = state.newEmail,
                        onValueChange = viewModel::onNewEmailChange,
                        label = stringResource(R.string.profile_new_email),
                        error = state.emailError,
                        leadingIcon = Icons.Filled.Email,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                        onImeAction = viewModel::sendEmailCode,
                    )
                    Text(stringResource(R.string.profile_email_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LoadingButton(stringResource(R.string.profile_send_code), state.emailBusy, viewModel::sendEmailCode)
                } else {
                    Text(stringResource(R.string.profile_otp_sent_to, state.maskedEmail))
                    OtpTextField(state.otp, viewModel::onOtpChange, state.otpError, onDone = viewModel::confirmEmail)
                    LoadingButton(stringResource(R.string.profile_confirm_email), state.emailBusy, viewModel::confirmEmail)
                    SecondaryButton(stringResource(R.string.cancel), viewModel::cancelEmailChange, Modifier.fillMaxWidth())
                }
            }

            SectionCard {
                Text(stringResource(R.string.profile_password_section), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                PasswordTextField(
                    value = state.newPassword,
                    onValueChange = viewModel::onNewPasswordChange,
                    label = stringResource(R.string.forgot_new_password),
                    error = state.passwordError,
                    leadingIcon = Icons.Filled.Lock,
                )
                PasswordTextField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = stringResource(R.string.auth_confirm_password),
                    error = state.confirmError,
                    leadingIcon = Icons.Filled.Lock,
                    imeAction = ImeAction.Done,
                    onImeAction = viewModel::changePassword,
                )
                LoadingButton(stringResource(R.string.profile_change_password), state.passwordBusy, viewModel::changePassword)
            }
        }
    }
}
