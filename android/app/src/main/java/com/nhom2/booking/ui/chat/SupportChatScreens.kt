package com.nhom2.booking.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nhom2.booking.R
import com.nhom2.booking.data.model.ChatRoomSummary
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.format.formatDateTime

/** Runs [block] while the screen is at least started, e.g. to keep the chat socket open only when visible. */
@Composable
fun WhileVisible(block: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { block() }
    }
}

/** A guest's conversation with support, or an admin's view of one guest's conversation. */
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    title: String,
    viewerIsAdmin: Boolean,
    onMessage: (UiText) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.messages.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})
    WhileVisible { viewModel.listen() }

    Scaffold(topBar = { AppTopBar(title, onBack = onBack) }) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is LoadState.Success -> ChatLayout(
                messageCount = current.data.size,
                input = input,
                onInputChange = viewModel::onInputChange,
                sending = sending,
                onSend = viewModel::send,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                header = if (viewerIsAdmin) null else {
                    {
                        item {
                            Text(
                                stringResource(R.string.support_chat_intro),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                            )
                        }
                    }
                },
            ) {
                items(current.data, key = { it.id }) { message ->
                    val mine = message.fromAdmin == viewerIsAdmin
                    val author = if (message.fromAdmin) stringResource(R.string.chat_support_team) else message.senderUsername.orEmpty()
                    ChatBubble(
                        text = message.content,
                        mine = mine,
                        caption = (if (mine) "" else "$author · ") + formatDateTime(message.createdAt),
                    )
                }
            }
        }
    }
}

@Composable
fun AdminInboxScreen(
    viewModel: AdminInboxViewModel,
    onMessage: (UiText) -> Unit,
    onOpenRoom: (ChatRoomSummary) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.rooms.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})
    WhileVisible { viewModel.listen() }
    // Coming back from a conversation should show it as read.
    WhileVisible { viewModel.load() }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.admin_support_inbox), onBack = onBack) }) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is LoadState.Success -> if (current.data.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.admin_inbox_empty), body = null, modifier = Modifier.padding(padding))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    items(current.data, key = { it.roomId }) { room ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRoom(room) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    room.username.orEmpty(),
                                    fontWeight = if (room.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                                )
                                Text(
                                    room.lastMessagePreview.orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(formatDateTime(room.lastMessageAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (room.unreadCount > 0) Badge { Text(room.unreadCount.toString()) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
