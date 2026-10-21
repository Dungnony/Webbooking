package com.nhom2.booking.ui.chat

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AccountApi
import com.nhom2.booking.data.api.AdminApi
import com.nhom2.booking.data.chat.SupportChatSocket
import com.nhom2.booking.data.model.ChatRoomSummary
import com.nhom2.booking.data.model.SendMessageBody
import com.nhom2.booking.data.model.SupportMessage
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "SupportChat"

/** Appends unless already there: the sender gets both the REST response and the WebSocket push. */
fun List<SupportMessage>.appendUnique(message: SupportMessage): List<SupportMessage> =
    if (any { it.id == message.id }) this else this + message

/**
 * One support conversation, seen by the guest who owns it or by an admin. Messages are sent over REST and
 * pushed back in real time over the socket.
 */
abstract class ConversationViewModel(private val socket: SupportChatSocket) : EventViewModel() {

    private val _messages = MutableStateFlow<LoadState<List<SupportMessage>>>(LoadState.Loading)
    val messages: StateFlow<LoadState<List<SupportMessage>>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    protected abstract suspend fun fetchMessages(): List<SupportMessage>
    protected abstract suspend fun postMessage(content: String): SupportMessage
    protected abstract suspend fun markRead()
    protected abstract fun belongsHere(message: SupportMessage): Boolean

    fun load() {
        viewModelScope.launch {
            _messages.value = LoadState.Loading
            _messages.value = try {
                LoadState.Success(fetchMessages()).also { markReadQuietly() }
            } catch (e: ApiException) {
                LoadState.Error(e.toUiText(UiText.Res(R.string.chat_load_failed)))
            }
        }
    }

    /** Receives pushed messages while the caller is active (the screen runs it only while visible). */
    suspend fun listen() {
        socket.messages().collect { message ->
            if (!belongsHere(message)) return@collect
            add(message)
            if (message.fromAdmin != isAdminSide) markReadQuietly()
        }
    }

    protected abstract val isAdminSide: Boolean

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _sending.value) return
        _sending.value = true
        viewModelScope.launch {
            try {
                add(postMessage(text))
                _input.value = ""
            } catch (e: ApiException) {
                showError(e, R.string.chat_send_failed)
            } finally {
                _sending.value = false
            }
        }
    }

    private fun add(message: SupportMessage) {
        val current = (_messages.value as? LoadState.Success)?.data ?: emptyList()
        _messages.value = LoadState.Success(current.appendUnique(message))
    }

    private suspend fun markReadQuietly() {
        try {
            markRead()
        } catch (e: ApiException) {
            Log.w(TAG, "Could not mark the conversation read", e)
        }
    }
}

class GuestSupportChatViewModel(
    private val accountApi: AccountApi,
    socket: SupportChatSocket,
) : ConversationViewModel(socket) {
    override val isAdminSide = false

    init {
        load()
    }

    override suspend fun fetchMessages() = apiCall { accountApi.getSupportChat() }.messages
    override suspend fun postMessage(content: String) = apiCall { accountApi.sendSupportMessage(SendMessageBody(content)) }
    override suspend fun markRead() = apiCall { accountApi.markSupportChatRead() }

    // The guest's personal queue only ever carries their own conversation.
    override fun belongsHere(message: SupportMessage) = true
}

class AdminConversationViewModel(
    private val roomId: Long,
    private val adminApi: AdminApi,
    socket: SupportChatSocket,
) : ConversationViewModel(socket) {
    override val isAdminSide = true

    init {
        load()
    }

    override suspend fun fetchMessages() = apiCall { adminApi.getChatMessages(roomId) }
    override suspend fun postMessage(content: String) = apiCall { adminApi.replyInChat(roomId, SendMessageBody(content)) }
    override suspend fun markRead() = apiCall { adminApi.markChatRead(roomId) }

    // Admins receive every conversation on one queue.
    override fun belongsHere(message: SupportMessage) = message.roomId == roomId
}

/** Admin inbox: one row per guest conversation, newest activity first. */
class AdminInboxViewModel(
    private val adminApi: AdminApi,
    private val socket: SupportChatSocket,
) : EventViewModel() {

    private val _rooms = MutableStateFlow<LoadState<List<ChatRoomSummary>>>(LoadState.Loading)
    val rooms: StateFlow<LoadState<List<ChatRoomSummary>>> = _rooms.asStateFlow()

    // No load in init: the screen reloads every time it becomes visible.

    fun load() {
        viewModelScope.launch {
            if (_rooms.value !is LoadState.Success) _rooms.value = LoadState.Loading
            try {
                _rooms.value = LoadState.Success(apiCall { adminApi.getChatRooms() }.sortedByDescending { it.lastMessageAt })
            } catch (e: ApiException) {
                if (_rooms.value is LoadState.Success) {
                    showError(e, R.string.chat_load_failed)
                } else {
                    _rooms.value = LoadState.Error(e.toUiText(UiText.Res(R.string.chat_load_failed)))
                }
            }
        }
    }

    /** Reloads the list whenever a message arrives, so previews and unread counts stay current. */
    suspend fun listen() {
        socket.messages().collect { load() }
    }
}
