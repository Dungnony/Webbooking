package com.nhom2.booking.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AdminApi
import com.nhom2.booking.data.model.Review
import com.nhom2.booking.data.model.ReviewReplyBody
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ConfirmDialog
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.RatingBadge
import com.nhom2.booking.ui.components.SectionCard
import com.nhom2.booking.ui.components.TextInputDialog
import com.nhom2.booking.ui.format.formatDate
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 10

data class AdminReviewsState(
    val items: LoadState<List<Review>> = LoadState.Loading,
    val nextPage: Int = 0,
    val totalPages: Int = 0,
    val unansweredOnly: Boolean = false,
    val loadingMore: Boolean = false,
    val busy: Boolean = false,
) {
    val hasMore: Boolean get() = nextPage < totalPages
}

class AdminReviewsViewModel(private val adminApi: AdminApi) : EventViewModel() {

    private val _state = MutableStateFlow(AdminReviewsState())
    val state: StateFlow<AdminReviewsState> = _state.asStateFlow()

    init {
        reload()
    }

    fun setUnansweredOnly(value: Boolean) {
        _state.update { it.copy(unansweredOnly = value) }
        reload()
    }

    fun reload() {
        _state.update { it.copy(items = LoadState.Loading, nextPage = 0, totalPages = 0) }
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = apiCall { adminApi.getReviews(current.nextPage, PAGE_SIZE, current.unansweredOnly) }
                _state.update {
                    val existing = (it.items as? LoadState.Success)?.data.orEmpty()
                    it.copy(
                        items = LoadState.Success(existing + page.content.filter { review -> existing.none { old -> old.id == review.id } }),
                        nextPage = page.page + 1,
                        totalPages = page.totalPages,
                    )
                }
            } catch (e: ApiException) {
                if (_state.value.items is LoadState.Success) {
                    showError(e, R.string.reviews_load_failed)
                } else {
                    _state.update { it.copy(items = LoadState.Error(e.toUiText(UiText.Res(R.string.reviews_load_failed)))) }
                }
            } finally {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun reply(review: Review, text: String) = mutate(R.string.admin_reply_saved) { adminApi.replyToReview(review.id, ReviewReplyBody(text)) }

    fun removeReply(review: Review) = mutate(R.string.admin_reply_removed) { adminApi.removeReviewReply(review.id) }

    private fun mutate(successRes: Int, action: suspend () -> Review) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val updated = apiCall { action() }
                showMessage(successRes)
                _state.update { state ->
                    val items = (state.items as? LoadState.Success)?.data.orEmpty()
                    // An answered review leaves the "unanswered" list.
                    val next = if (state.unansweredOnly && !updated.hotelReply.isNullOrBlank()) {
                        items.filterNot { it.id == updated.id }
                    } else {
                        items.map { if (it.id == updated.id) updated else it }
                    }
                    state.copy(items = LoadState.Success(next))
                }
            } catch (e: ApiException) {
                showError(e)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

@Composable
fun AdminReviewsScreen(viewModel: AdminReviewsViewModel, onMessage: (UiText) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var replying by remember { mutableStateOf<Review?>(null) }
    var removing by remember { mutableStateOf<Review?>(null) }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(topBar = { AppTopBar(stringResource(R.string.admin_reviews_title), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.admin_unanswered_only), modifier = Modifier.weight(1f))
                Switch(checked = state.unansweredOnly, onCheckedChange = viewModel::setUnansweredOnly)
            }
            when (val items = state.items) {
                LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(items.message, onRetry = viewModel::reload)
                is LoadState.Success -> if (items.data.isEmpty()) {
                    EmptyState(Icons.Filled.RateReview, stringResource(R.string.admin_list_empty), body = null)
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(items.data, key = { it.id }) { review ->
                            AdminReviewCard(
                                review = review,
                                enabled = !state.busy,
                                onReply = { replying = review },
                                onRemoveReply = { removing = review },
                            )
                        }
                        if (state.hasMore) {
                            item {
                                OutlinedButton(onClick = viewModel::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.reviews_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    replying?.let { review ->
        TextInputDialog(
            title = stringResource(R.string.admin_reply_title, review.username.orEmpty()),
            label = stringResource(R.string.admin_reply_label),
            confirmLabel = stringResource(R.string.send),
            initialValue = review.hotelReply.orEmpty(),
            required = true,
            onConfirm = {
                viewModel.reply(review, it)
                replying = null
            },
            onDismiss = { replying = null },
        )
    }
    removing?.let { review ->
        ConfirmDialog(
            title = stringResource(R.string.admin_remove_reply),
            text = stringResource(R.string.admin_remove_reply_confirm),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.removeReply(review)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun AdminReviewCard(review: Review, enabled: Boolean, onReply: () -> Unit, onRemoveReply: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RatingBadge(review.rating?.toDouble())
            Column(modifier = Modifier.weight(1f)) {
                Text(review.hotelName.orEmpty(), fontWeight = FontWeight.Bold)
                Text("${review.username.orEmpty()} · ${formatDate(review.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!review.comment.isNullOrBlank()) Text(review.comment)
        if (!review.hotelReply.isNullOrBlank()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(stringResource(R.string.review_hotel_reply), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(review.hotelReply, fontSize = 14.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReply, enabled = enabled) {
                Text(stringResource(if (review.hotelReply.isNullOrBlank()) R.string.admin_reply else R.string.admin_edit_reply))
            }
            if (!review.hotelReply.isNullOrBlank()) {
                TextButton(onClick = onRemoveReply, enabled = enabled) {
                    Text(stringResource(R.string.admin_remove_reply), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
