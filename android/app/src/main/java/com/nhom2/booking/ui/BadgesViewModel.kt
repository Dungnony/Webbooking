package com.nhom2.booking.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.data.api.AccountApi
import com.nhom2.booking.data.api.AdminApi
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.data.session.SessionState
import com.nhom2.booking.data.session.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BadgesViewModel"
// Same interval the web header polls notifications at.
private const val POLL_INTERVAL_MS = 60_000L

/** Unread notification and support-chat counts shown on the bottom bar. */
class BadgesViewModel(
    private val accountApi: AccountApi,
    private val adminApi: AdminApi,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications.asStateFlow()

    private val _unreadChat = MutableStateFlow(0)
    val unreadChat: StateFlow<Int> = _unreadChat.asStateFlow()

    /** Polls while the caller is active (the screen collects it only while visible). */
    suspend fun pollWhileVisible() {
        while (true) {
            refreshNow()
            delay(POLL_INTERVAL_MS)
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val session = (sessionStore.state.value as? SessionState.LoggedIn)?.session
        if (session == null) {
            _unreadNotifications.value = 0
            _unreadChat.value = 0
            return
        }
        try {
            _unreadNotifications.value = apiCall { accountApi.getNotifications() }.count { it.read != true }
            _unreadChat.value = apiCall {
                if (session.isAdmin) adminApi.getChatUnreadCount() else accountApi.getSupportUnreadCount()
            }.count.toInt()
        } catch (e: ApiException) {
            Log.w(TAG, "Could not refresh unread counts", e)
        }
    }
}
