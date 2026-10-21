package com.nhom2.booking.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val session: Session) : SessionState
}

class SessionStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    val state: StateFlow<SessionState> = dataStore.data
        .map { prefs ->
            val session = prefs[TOKEN_KEY]
                ?.let(Session::fromToken)
                ?.takeUnless { it.isExpired(System.currentTimeMillis()) }
            if (session == null) SessionState.LoggedOut else SessionState.LoggedIn(session)
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /** Read by the HTTP interceptor, which runs off the main thread and cannot suspend. */
    fun currentToken(): String? = (state.value as? SessionState.LoggedIn)?.session?.token

    /** Returns null without saving anything when the token cannot be read. */
    suspend fun save(token: String): Session? {
        val session = Session.fromToken(token) ?: return null
        dataStore.edit { it[TOKEN_KEY] = token }
        return session
    }

    suspend fun clear() {
        dataStore.edit { it.remove(TOKEN_KEY) }
    }

    private companion object {
        val TOKEN_KEY = stringPreferencesKey("token")
    }
}
