package com.nhom2.booking.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AdminApi
import com.nhom2.booking.data.model.BookingRequestItem
import com.nhom2.booking.data.model.ProcessRequestBody
import com.nhom2.booking.data.model.RequestStatus
import com.nhom2.booking.data.model.RequestType
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.domain.bookingCode
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.InfoRow
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.SectionCard
import com.nhom2.booking.ui.components.StatusChip
import com.nhom2.booking.ui.components.TextInputDialog
import com.nhom2.booking.ui.format.formatDate
import com.nhom2.booking.ui.format.formatDateTime
import com.nhom2.booking.ui.format.formatVnd
import com.nhom2.booking.ui.format.paymentStatusLabel
import com.nhom2.booking.ui.format.requestStatusColor
import com.nhom2.booking.ui.format.requestStatusLabel
import com.nhom2.booking.ui.format.requestTypeLabel

private val REQUEST_FILTERS = listOf(null, RequestStatus.PENDING, RequestStatus.APPROVED, RequestStatus.REJECTED)

class AdminRequestsViewModel(private val adminApi: AdminApi) : AdminListViewModel<BookingRequestItem>() {
    init {
        // Pending requests are the ones an admin has to act on.
        setFilter(RequestStatus.PENDING)
    }

    override suspend fun fetch(filter: String?) = apiCall { adminApi.getBookingRequests(filter) }.sortedByDescending { it.id }

    fun approve(id: Long, response: String) =
        runAction(id, R.string.admin_request_approved) { adminApi.approveRequest(id, ProcessRequestBody(response.ifBlank { null })) }

    fun reject(id: Long, response: String) =
        runAction(id, R.string.admin_request_rejected) { adminApi.rejectRequest(id, ProcessRequestBody(response.ifBlank { null })) }
}

private enum class Decision { APPROVE, REJECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRequestsScreen(viewModel: AdminRequestsViewModel, onMessage: (UiText) -> Unit) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val busyId by viewModel.busyId.collectAsStateWithLifecycle()
    var decision by rememberSaveable { mutableStateOf<Pair<Long, Decision>?>(null) }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(topBar = { AppTopBar(stringResource(R.string.admin_requests_title)) }) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = viewModel::load, modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::load)
                is LoadState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(REQUEST_FILTERS) { status ->
                                FilterChip(
                                    selected = filter == status,
                                    onClick = { viewModel.setFilter(status) },
                                    label = { Text(if (status == null) stringResource(R.string.filter_all) else stringResource(requestStatusLabel(status))) },
                                )
                            }
                        }
                    }
                    if (current.data.isEmpty()) {
                        item { EmptyState(Icons.Filled.Inbox, stringResource(R.string.admin_list_empty), body = null, modifier = Modifier.height(300.dp)) }
                    }
                    items(current.data, key = { it.id }) { request ->
                        RequestCard(
                            request = request,
                            busy = busyId == request.id,
                            onApprove = { decision = request.id to Decision.APPROVE },
                            onReject = { decision = request.id to Decision.REJECT },
                        )
                    }
                }
            }
        }
    }

    decision?.let { (id, kind) ->
        TextInputDialog(
            title = stringResource(if (kind == Decision.APPROVE) R.string.admin_approve_request else R.string.admin_reject_request),
            label = stringResource(R.string.admin_response_label),
            confirmLabel = stringResource(if (kind == Decision.APPROVE) R.string.admin_approve else R.string.admin_reject),
            onConfirm = { response ->
                if (kind == Decision.APPROVE) viewModel.approve(id, response) else viewModel.reject(id, response)
                decision = null
            },
            onDismiss = { decision = null },
        )
    }
}

@Composable
private fun RequestCard(request: BookingRequestItem, busy: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    val booking = request.booking
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(requestTypeLabel(request.type)), fontWeight = FontWeight.Bold)
                if (booking != null) Text(bookingCode(booking.id) + " · " + booking.hotel?.name.orEmpty(), fontSize = 14.sp)
            }
            StatusChip(stringResource(requestStatusLabel(request.status)), requestStatusColor(request.status))
        }
        if (booking != null) {
            InfoRow(stringResource(R.string.admin_guest), booking.user?.username.orEmpty())
            InfoRow(stringResource(R.string.admin_stay), "${formatDate(booking.checkInDate)} → ${formatDate(booking.checkOutDate)}")
            InfoRow(stringResource(R.string.booking_payment), stringResource(paymentStatusLabel(booking.paymentStatus)))
            InfoRow(stringResource(R.string.booking_total), formatVnd(booking.totalPrice))
        }
        if (request.type == RequestType.CHANGE_DATE) {
            InfoRow(stringResource(R.string.admin_new_dates), "${formatDate(request.newCheckIn)} → ${formatDate(request.newCheckOut)}")
        }
        if (!request.reason.isNullOrBlank()) InfoRow(stringResource(R.string.bookings_request_reason), request.reason)
        InfoRow(stringResource(R.string.booking_created_at), formatDateTime(request.createdAt))
        if (!request.adminResponse.isNullOrBlank()) InfoRow(stringResource(R.string.admin_response_label), request.adminResponse)
        if (request.status == RequestStatus.PENDING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = !busy) { Text(stringResource(R.string.admin_approve)) }
                TextButton(onClick = onReject, enabled = !busy) {
                    Text(stringResource(R.string.admin_reject), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
