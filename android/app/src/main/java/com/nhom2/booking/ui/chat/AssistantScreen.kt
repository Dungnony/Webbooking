package com.nhom2.booking.ui.chat

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AccountApi
import com.nhom2.booking.data.catalog.HotelRepository
import com.nhom2.booking.data.model.AiChatBody
import com.nhom2.booking.data.model.AiHotelInfo
import com.nhom2.booking.data.model.AiMessage
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.data.session.SessionStore
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.components.AppTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Context the web assistant sends when the question is not about one hotel.
private val GENERAL_INFO = AiHotelInfo(
    name = "Hệ thống Booking Nhom2",
    description = "Hệ thống đặt phòng trực tuyến tại Hà Nội.",
    city = "Hà Nội",
    ratingAvg = 9.5,
)

private val GENERAL_QUESTIONS = listOf(
    R.string.assistant_q_hotels,
    R.string.assistant_q_prices,
    R.string.assistant_q_how_to_book,
    R.string.assistant_q_cancel,
    R.string.assistant_q_payment,
)

private val HOTEL_QUESTIONS = listOf(
    R.string.assistant_q_amenities,
    R.string.assistant_q_room_price,
    R.string.assistant_q_check_in,
    R.string.assistant_q_directions,
    R.string.assistant_q_cancel,
)

class AssistantViewModel(
    private val hotelId: Long?,
    private val accountApi: AccountApi,
    private val hotelRepository: HotelRepository,
    private val sessionStore: SessionStore,
    private val appContext: Context,
) : EventViewModel() {

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _hotelName = MutableStateFlow<String?>(null)
    val hotelName: StateFlow<String?> = _hotelName.asStateFlow()

    private var hotelInfo: AiHotelInfo = GENERAL_INFO

    val quickQuestions: List<Int> = if (hotelId == null) GENERAL_QUESTIONS else HOTEL_QUESTIONS

    init {
        if (hotelId != null) {
            viewModelScope.launch {
                try {
                    val hotel = hotelRepository.getHotel(hotelId)
                    _hotelName.value = hotel.name
                    hotelInfo = AiHotelInfo(hotel.name, hotel.description, hotel.address, hotel.city, hotel.ratingAvg)
                } catch (e: ApiException) {
                    showError(e, R.string.hotel_load_failed)
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun ask(@StringRes questionRes: Int) = send(appContext.getString(questionRes))

    fun send(text: String = _input.value) {
        val question = text.trim()
        if (question.isEmpty() || _thinking.value) return
        _input.value = ""
        val conversation = _messages.value + AiMessage(AiMessage.ROLE_USER, question)
        _messages.value = conversation
        // The chat endpoint requires a login so anonymous visitors cannot spend the AI quota.
        if (sessionStore.currentToken() == null) {
            reply(appContext.getString(R.string.assistant_login_required))
            return
        }
        _thinking.value = true
        viewModelScope.launch {
            try {
                val answer = apiCall { accountApi.askAssistant(AiChatBody(conversation, hotelInfo)) }.reply
                reply(answer?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.assistant_fallback_reply))
            } catch (e: ApiException) {
                reply(appContext.getString(R.string.assistant_connection_error))
            } finally {
                _thinking.value = false
            }
        }
    }

    fun reset() {
        _messages.value = emptyList()
        _input.value = ""
    }

    private fun reply(text: String) {
        _messages.value = _messages.value + AiMessage(AiMessage.ROLE_ASSISTANT, text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(viewModel: AssistantViewModel, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val thinking by viewModel.thinking.collectAsStateWithLifecycle()
    val hotelName by viewModel.hotelName.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = hotelName?.let { stringResource(R.string.assistant_title_hotel, it) } ?: stringResource(R.string.assistant_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::reset) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.assistant_reset))
                    }
                },
            )
        },
    ) { padding ->
        ChatLayout(
            messageCount = messages.size + if (thinking) 1 else 0,
            input = input,
            onInputChange = viewModel::onInputChange,
            sending = thinking,
            onSend = { viewModel.send() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            header = {
                item {
                    ChatBubble(stringResource(R.string.assistant_greeting), mine = false, caption = null)
                }
            },
            footer = if (messages.isEmpty()) {
                {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        viewModel.quickQuestions.forEach { question ->
                            AssistChip(onClick = { viewModel.ask(question) }, label = { Text(stringResource(question)) })
                        }
                    }
                }
            } else {
                null
            },
        ) {
            itemsIndexed(messages) { _, message ->
                ChatBubble(message.content, mine = message.role == AiMessage.ROLE_USER, caption = null)
            }
            if (thinking) {
                item {
                    Text(
                        stringResource(R.string.assistant_thinking),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
