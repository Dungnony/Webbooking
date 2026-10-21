package com.nhom2.booking.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AccountApi
import com.nhom2.booking.data.model.NotificationItem
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.format.formatDateTime
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val accountApi: AccountApi,
    /** Lets the tab badge update right away instead of on its next poll. */
    private val onChanged: () -> Unit,
) : EventViewModel() {

    private val _notifications = MutableStateFlow<LoadState<List<NotificationItem>>>(LoadState.Loading)
    val notifications: StateFlow<LoadState<List<NotificationItem>>> = _notifications.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_notifications.value is LoadState.Success) _refreshing.value = true
            try {
                _notifications.value = LoadState.Success(apiCall { accountApi.getNotifications() }.sortedByDescending { it.id })
            } catch (e: ApiException) {
                if (_notifications.value !is LoadState.Success) {
                    _notifications.value = LoadState.Error(e.toUiText(UiText.Res(R.string.notifications_load_failed)))
                } else {
                    showError(e, R.string.notifications_load_failed)
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun markRead(notification: NotificationItem) {
        if (notification.read == true) return
        viewModelScope.launch {
            try {
                val updated = apiCall { accountApi.markNotificationRead(notification.id) }
                replace { list -> list.map { if (it.id == updated.id) updated else it } }
                onChanged()
            } catch (e: ApiException) {
                showError(e)
            }
        }
    }

    fun delete(notification: NotificationItem) {
        viewModelScope.launch {
            try {
                apiCall { accountApi.deleteNotification(notification.id) }
                replace { list -> list.filterNot { it.id == notification.id } }
                onChanged()
            } catch (e: ApiException) {
                showError(e)
            }
        }
    }

    private fun replace(transform: (List<NotificationItem>) -> List<NotificationItem>) {
        (_notifications.value as? LoadState.Success)?.let { _notifications.value = LoadState.Success(transform(it.data)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onMessage: (UiText) -> Unit, onBack: () -> Unit) {
    val state by viewModel.notifications.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(topBar = { AppTopBar(stringResource(R.string.notifications_title), onBack = onBack) }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::load,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::load)
                is LoadState.Success -> if (current.data.isEmpty()) {
                    EmptyState(Icons.Filled.NotificationsNone, stringResource(R.string.notifications_empty), body = null)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(current.data, key = { it.id }) { notification ->
                            NotificationRow(notification, onClick = { viewModel.markRead(notification) }, onDelete = { viewModel.delete(notification) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: NotificationItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val unread = notification.read != true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (unread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, CircleShape))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(notification.title.orEmpty(), fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold)
            Text(notification.message.orEmpty(), fontSize = 14.sp)
            Text(formatDateTime(notification.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
