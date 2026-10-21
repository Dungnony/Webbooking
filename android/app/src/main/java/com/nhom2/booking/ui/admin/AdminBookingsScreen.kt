package com.nhom2.booking.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
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
import com.nhom2.booking.data.model.Booking
import com.nhom2.booking.data.model.BookingStatus
import com.nhom2.booking.data.model.ProcessRequestBody
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.domain.bookingCode
import com.nhom2.booking.domain.canAdminDecide
import com.nhom2.booking.domain.canMarkPaid
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ConfirmDialog
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.InfoRow
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.SectionCard
import com.nhom2.booking.ui.components.StatusChip
import com.nhom2.booking.ui.components.TextInputDialog
import com.nhom2.booking.ui.format.bookingStatusColor
import com.nhom2.booking.ui.format.bookingStatusLabel
import com.nhom2.booking.ui.format.formatDate
import com.nhom2.booking.ui.format.formatDateTime
import com.nhom2.booking.ui.format.formatVnd
import com.nhom2.booking.ui.format.paymentStatusLabel
import com.nhom2.booking.ui.LoadState

private val BOOKING_FILTERS = listOf(null, BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.REJECTED)

class AdminBookingsViewModel(private val adminApi: AdminApi) : AdminListViewModel<Booking>() {
    init {
        load()
    }

    override suspend fun fetch(filter: String?) = apiCall { adminApi.getBookings(filter) }.sortedByDescending { it.id }

    fun approve(id: Long) = runAction(id, R.string.admin_booking_approved) { adminApi.approveBooking(id) }

    fun reject(id: Long, reason: String) =
        runAction(id, R.string.admin_booking_rejected) { adminApi.rejectBooking(id, ProcessRequestBody(reason.ifBlank { null })) }

    fun markPaid(id: Long) = runAction(id, R.string.admin_booking_marked_paid) { adminApi.markBookingPaid(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBookingsScreen(viewModel: AdminBookingsViewModel, onMessage: (UiText) -> Unit) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val busyId by viewModel.busyId.collectAsStateWithLifecycle()
    var rejectTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var paidTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(topBar = { AppTopBar(stringResource(R.string.admin_bookings_title)) }) { padding ->
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
                            items(BOOKING_FILTERS) { status ->
                                FilterChip(
                                    selected = filter == status,
                                    onClick = { viewModel.setFilter(status) },
                                    label = { Text(if (status == null) stringResource(R.string.filter_all) else stringResource(bookingStatusLabel(status))) },
                                )
                            }
                        }
                    }
                    if (current.data.isEmpty()) {
                        item { EmptyState(Icons.Filled.Inbox, stringResource(R.string.admin_list_empty), body = null, modifier = Modifier.height(300.dp)) }
                    }
                    items(current.data, key = { it.id }) { booking ->
                        AdminBookingCard(
                            booking = booking,
                            busy = busyId == booking.id,
                            onApprove = { viewModel.approve(booking.id) },
                            onReject = { rejectTarget = booking.id },
                            onMarkPaid = { paidTarget = booking.id },
                        )
                    }
                }
            }
        }
    }

    rejectTarget?.let { id ->
        TextInputDialog(
            title = stringResource(R.string.admin_reject_booking_title, bookingCode(id)),
            label = stringResource(R.string.admin_reject_reason),
            confirmLabel = stringResource(R.string.admin_reject),
            onConfirm = {
                viewModel.reject(id, it)
                rejectTarget = null
            },
            onDismiss = { rejectTarget = null },
        )
    }
    paidTarget?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.admin_mark_paid),
            text = stringResource(R.string.admin_mark_paid_confirm, bookingCode(id)),
            confirmLabel = stringResource(R.string.admin_mark_paid),
            onConfirm = {
                viewModel.markPaid(id)
                paidTarget = null
            },
            onDismiss = { paidTarget = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminBookingCard(
    booking: Booking,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onMarkPaid: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bookingCode(booking.id), fontWeight = FontWeight.Bold)
                Text(booking.hotel?.name.orEmpty(), fontSize = 14.sp)
            }
            StatusChip(stringResource(bookingStatusLabel(booking.status)), bookingStatusColor(booking.status))
        }
        InfoRow(stringResource(R.string.admin_guest), listOfNotNull(booking.user?.username, booking.user?.email).joinToString(" · "))
        InfoRow(stringResource(R.string.admin_stay), "${formatDate(booking.checkInDate)} → ${formatDate(booking.checkOutDate)}")
        InfoRow(stringResource(R.string.booking_payment), stringResource(paymentStatusLabel(booking.paymentStatus)))
        InfoRow(stringResource(R.string.booking_created_at), formatDateTime(booking.createdAt))
        InfoRow(stringResource(R.string.booking_total), formatVnd(booking.totalPrice), emphasize = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (booking.canAdminDecide()) {
                Button(onClick = onApprove, enabled = !busy) { Text(stringResource(R.string.admin_approve)) }
                TextButton(onClick = onReject, enabled = !busy) {
                    Text(stringResource(R.string.admin_reject), color = MaterialTheme.colorScheme.error)
                }
            }
            if (booking.canMarkPaid()) {
                OutlinedButton(onClick = onMarkPaid, enabled = !busy) { Text(stringResource(R.string.admin_mark_paid)) }
            }
        }
    }
}
