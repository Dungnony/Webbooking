package com.nhom2.booking.data.wishlist

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nhom2.booking.data.api.AccountApi
import com.nhom2.booking.data.model.UserRole
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.data.session.SessionState
import com.nhom2.booking.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "WishlistRepository"

/**
 * Saved hotel ids, newest first. Visitors keep them on the device; once they sign in the device list is merged
 * into their account (same behaviour as the web app) and the server becomes the source of truth.
 */
class WishlistRepository(
    private val api: AccountApi,
    private val guestStore: DataStore<Preferences>,
    private val sessionStore: SessionStore,
    scope: CoroutineScope,
) {
    private val _ids = MutableStateFlow<List<Long>>(emptyList())
    val ids: StateFlow<List<Long>> = _ids.asStateFlow()

    init {
        scope.launch {
            sessionStore.state
                .map { (it as? SessionState.LoggedIn)?.session }
                .distinctUntilChanged { old, new -> old?.username == new?.username }
                .collect { session ->
                    when {
                        session == null -> _ids.value = readGuestIds()
                        // Admins manage hotels and have no wishlist.
                        session.role == UserRole.ADMIN -> _ids.value = emptyList()
                        else -> syncAfterLogin()
                    }
                }
        }
    }

    private val isSignedIn: Boolean
        get() = sessionStore.currentToken() != null

    suspend fun toggle(hotelId: Long) {
        val saved = hotelId in _ids.value
        if (isSignedIn) {
            _ids.value = apiCall {
                if (saved) api.removeFromWishlist(hotelId) else api.addToWishlist(hotelId)
            }
        } else {
            val updated = if (saved) _ids.value - hotelId else listOf(hotelId) + _ids.value
            writeGuestIds(updated)
            _ids.value = updated
        }
    }

    suspend fun clear() {
        if (isSignedIn) apiCall { api.clearWishlist() } else writeGuestIds(emptyList())
        _ids.value = emptyList()
    }

    /** Re-reads the list from the server, e.g. when the wishlist screen opens. */
    suspend fun refresh() {
        if (isSignedIn && (sessionStore.state.value as? SessionState.LoggedIn)?.session?.isAdmin == false) {
            _ids.value = apiCall { api.getWishlist() }
        }
    }

    private suspend fun syncAfterLogin() {
        try {
            val guestIds = readGuestIds()
            _ids.value = if (guestIds.isEmpty()) {
                apiCall { api.getWishlist() }
            } else {
                apiCall { api.mergeWishlist(guestIds) }.also { writeGuestIds(emptyList()) }
            }
        } catch (e: ApiException) {
            // Not fatal: the hearts just show empty until the next refresh.
            Log.w(TAG, "Could not load the wishlist", e)
        }
    }

    private suspend fun readGuestIds(): List<Long> =
        guestStore.data.first()[GUEST_IDS_KEY]
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()

    private suspend fun writeGuestIds(ids: List<Long>) {
        guestStore.edit { it[GUEST_IDS_KEY] = ids.joinToString(",") }
    }

    private companion object {
        val GUEST_IDS_KEY = stringPreferencesKey("guest_wishlist")
    }
}
