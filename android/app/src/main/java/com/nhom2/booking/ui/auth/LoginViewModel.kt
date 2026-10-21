package com.nhom2.booking.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.auth.AuthRepository
import com.nhom2.booking.data.network.ApiException
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

// Backend locks an account for 15 minutes after repeated wrong passwords.
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val usernameError: UiText? = null,
    val passwordError: UiText? = null,
    val loading: Boolean = false,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, usernameError = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val usernameError = if (current.username.isBlank()) UiText.Res(R.string.auth_username_required) else null
        val passwordError = if (current.password.isEmpty()) UiText.Res(R.string.auth_password_required) else null
        if (usernameError != null || passwordError != null) {
            _state.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
            return
        }

        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                authRepository.login(current.username.trim(), current.password)
                _events.send(UiEvent.ShowMessage(UiText.Res(R.string.auth_login_success)))
                _events.send(UiEvent.Completed)
            } catch (e: ApiException) {
                _events.send(UiEvent.ShowMessage(loginErrorText(e)))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun onGoogleResult(result: GoogleSignInResult) {
        when (result) {
            GoogleSignInResult.Cancelled -> Unit
            is GoogleSignInResult.Failed -> _events.trySend(UiEvent.ShowMessage(result.message))
            is GoogleSignInResult.Success -> loginWithGoogle(result.idToken)
        }
    }

    private fun loginWithGoogle(idToken: String) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                authRepository.loginWithGoogle(idToken)
                _events.send(UiEvent.ShowMessage(UiText.Res(R.string.google_success)))
                _events.send(UiEvent.Completed)
            } catch (e: ApiException) {
                _events.send(UiEvent.ShowMessage(e.toUiText(UiText.Res(R.string.google_failed))))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun loginErrorText(e: ApiException): UiText = when {
        e is ApiException.Http && e.status == HTTP_TOO_MANY_REQUESTS ->
            e.toUiText(UiText.Res(R.string.auth_too_many_attempts))
        // The backend answers the same way for an unknown user and a wrong password.
        e is ApiException.Http && (e.status == HTTP_UNAUTHORIZED || e.status == HTTP_BAD_REQUEST) ->
            UiText.Res(R.string.auth_wrong_credentials)
        e is ApiException.Http ->
            UiText.Res(R.string.error_generic, e.serverMessage ?: e.status.toString())
        else -> e.toUiText(UiText.Res(R.string.error_generic_failed))
    }
}
