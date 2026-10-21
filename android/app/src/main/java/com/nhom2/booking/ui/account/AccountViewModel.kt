package com.nhom2.booking.ui.account

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AccountApi
import com.nhom2.booking.data.auth.AuthRepository
import com.nhom2.booking.data.files.UnsupportedImageException
import com.nhom2.booking.data.files.imagePart
import com.nhom2.booking.data.model.UserProfile
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.data.session.SessionState
import com.nhom2.booking.data.session.SessionStore
import com.nhom2.booking.ui.EventViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AccountViewModel(
    private val accountApi: AccountApi,
    private val authRepository: AuthRepository,
    sessionStore: SessionStore,
    private val appContext: Context,
) : EventViewModel() {

    /** Null while signed out or until the profile has loaded. */
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.state
                .map { (it as? SessionState.LoggedIn)?.session?.username }
                .distinctUntilChanged()
                .collect { username -> if (username == null) _profile.value = null else loadProfile() }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            try {
                _profile.value = apiCall { accountApi.getMe() }
            } catch (e: ApiException) {
                showError(e, R.string.profile_load_failed)
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        if (_uploading.value) return
        _uploading.value = true
        viewModelScope.launch {
            try {
                val part = imagePart(appContext, uri)
                _profile.value = apiCall { accountApi.uploadAvatar(part) }
                showMessage(R.string.profile_avatar_updated)
            } catch (e: UnsupportedImageException) {
                showMessage(R.string.image_unsupported)
            } catch (e: ApiException) {
                showError(e, R.string.profile_avatar_failed)
            } finally {
                _uploading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            showMessage(R.string.logged_out)
        }
    }
}
