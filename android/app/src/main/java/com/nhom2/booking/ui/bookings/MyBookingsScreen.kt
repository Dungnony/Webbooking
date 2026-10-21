package com.nhom2.booking.ui.bookings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom2.booking.BuildConfig
import com.nhom2.booking.R
import com.nhom2.booking.data.model.Booking
import com.nhom2.booking.data.model.BookingStatus
import com.nhom2.booking.domain.bookingCode
import com.nhom2.booking.domain.canCancelDirectly
import com.nhom2.booking.domain.canDownloadInvoice
import com.nhom2.booking.domain.canPay
import com.nhom2.booking.domain.canRequestCancel
import com.nhom2.booking.domain.coverImage
import com.nhom2.booking.domain.nights
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.asString
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ConfirmDialog
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.InfoRow
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.RemoteImage
import com.nhom2.booking.ui.components.StatusChip
import com.nhom2.booking.ui.components.TextInputDialog
import com.nhom2.booking.ui.format.bookingStatusColor
import com.nhom2.booking.ui.format.bookingStatusLabel
import com.nhom2.booking.ui.format.formatDate
import com.nhom2.booking.ui.format.formatDateTime
import com.nhom2.booking.ui.format.formatVnd
import com.nhom2.booking.ui.format.paymentStatusLabel
import com.nhom2.booking.ui.format.requestStatusColor
import com.nhom2.booking.ui.format.requestStatusLabel
import com.nhom2.booking.ui.format.requestTypeLabel
import java.time.LocalDate

private val STATUS_FILTERS = listOf(
    null,
    BookingStatus.PENDING,
    BookingStatus.CONFIRMED,
    BookingStatus.CANCELLED,
    BookingStatus.REJECTED,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    viewModel: MyBookingsViewModel,
    onMessage: (UiText) -> Unit,
    onPay: (Long) -> Unit,
    onOpenHotel: (Long) -> Unit,
    onExplore: () -> Unit,
) {
    val state by viewModel.bookings.collectAsStateWithLifecycle()
    val filter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val busyId by viewModel.busyBookingId.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    var cancelTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var refundTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})
    val today = remember { LocalDate.now() }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.tab_bookings)) }) { padding ->
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
                is LoadState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(STATUS_FILTERS) { status ->
                                FilterChip(
                                    selected = filter == status,
                                    onClick = { viewModel.setStatusFilter(status) },
                                    label = {
                                        Text(
                                            if (status == null) stringResource(R.string.filter_all)
                                            else stringResource(bookingStatusLabel(status)),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (current.data.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Filled.EventBusy,
                                title = stringResource(if (filter == null) R.string.bookings_empty else R.string.bookings_empty_filtered),
                                body = null,
                                actionLabel = stringResource(R.string.wishlist_explore),
                                onAction = onExplore,
                                modifier = Modifier.height(360.dp),
                            )
                        }
                    }
                    items(current.data, key = { it.id }) { booking ->
                        BookingCard(
                            booking = booking,
                            today = today,
                            busy = busyId == booking.id,
                            onPay = { onPay(booking.id) },
                            onDetails = { viewModel.showDetails(booking) },
                            onInvoice = { viewModel.downloadInvoice(booking.id) },
                            onCancel = { cancelTarget = booking.id },
                            onRequestRefund = { refundTarget = booking.id },
                        )
                    }
                }
            }
        }
    }

    cancelTarget?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.bookings_cancel_title),
            text = stringResource(R.string.bookings_cancel_confirm, bookingCode(id)),
            confirmLabel = stringResource(R.string.bookings_cancel),
            onConfirm = {
                viewModel.cancel(id)
                cancelTarget = null
            },
            onDismiss = { cancelTarget = null },
        )
    }
    refundTarget?.let { id ->
        TextInputDialog(
            title = stringResource(R.string.bookings_request_refund_title),
            label = stringResource(R.string.bookings_request_reason),
            confirmLabel = stringResource(R.string.send),
            onConfirm = { reason ->
                viewModel.requestCancel(id, reason)
                refundTarget = null
            },
            onDismiss = { refundTarget = null },
        )
    }
    details?.let { current ->
        BookingDetailsSheet(current, onOpenHotel = onOpenHotel, onDismiss = viewModel::hideDetails)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingCard(
    booking: Booking,
    today: LocalDate,
    busy: Boolean,
    onPay: () -> Unit,
    onDetails: () -> Unit,
    onInvoice: () -> Unit,
    onCancel: () -> Unit,
    onRequestRefund: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteImage(
                    url = booking.hotel?.coverImage(BuildConfig.API_BASE_URL),
                    modifier = Modifier.size(80.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        booking.hotel?.name ?: stringResource(R.string.hotel_fallback_name),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            " ${formatDate(booking.checkInDate)} → ${formatDate(booking.checkOutDate)}",
                            fontSize = 13.sp,
                        )
                    }
                    StatusChip(stringResource(bookingStatusLabel(booking.status)), bookingStatusColor(booking.status))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatVnd(booking.totalPrice), fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(paymentStatusLabel(booking.paymentStatus)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (booking.canPay()) {
                    Button(onClick = onPay, enabled = !busy) { Text(stringResource(R.string.bookings_pay)) }
                }
                OutlinedButton(onClick = onDetails) { Text(stringResource(R.string.bookings_details)) }
                if (booking.canDownloadInvoice()) {
                    OutlinedButton(onClick = onInvoice, enabled = !busy) { Text(stringResource(R.string.bookings_invoice)) }
                }
                if (booking.canCancelDirectly(today)) {
                    TextButton(onClick = onCancel, enabled = !busy) {
                        Text(stringResource(R.string.bookings_cancel), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (booking.canRequestCancel(today)) {
                    TextButton(onClick = onRequestRefund, enabled = !busy) {
                        Text(stringResource(R.string.bookings_request_refund), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingDetailsSheet(details: BookingDetails, onOpenHotel: (Long) -> Unit, onDismiss: () -> Unit) {
    val booking = details.booking
    val nights = booking.nights()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.bookings_details), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            InfoRow(stringResource(R.string.booking_code), bookingCode(booking.id))
            InfoRow(stringResource(R.string.booking_hotel), booking.hotel?.name.orEmpty())
            InfoRow(stringResource(R.string.booking_check_in), formatDate(booking.checkInDate))
            InfoRow(stringResource(R.string.booking_check_out), formatDate(booking.checkOutDate))
            InfoRow(stringResource(R.string.booking_nights_label), pluralStringResource(R.plurals.nights, nights, nights))
            booking.bookingRooms.forEach { item ->
                InfoRow(
                    item.room?.name ?: item.room?.type ?: stringResource(R.string.confirmation_room),
                    "${item.quantity ?: 1} × ${formatVnd(item.price)}",
                )
            }
            InfoRow(stringResource(R.string.booking_status), stringResource(bookingStatusLabel(booking.status)))
            InfoRow(stringResource(R.string.booking_payment), stringResource(paymentStatusLabel(booking.paymentStatus)))
            InfoRow(stringResource(R.string.booking_created_at), formatDateTime(booking.createdAt))
            InfoRow(stringResource(R.string.booking_total), formatVnd(booking.totalPrice), emphasize = true)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.bookings_requests_title), fontWeight = FontWeight.Bold)
            when (val requests = details.requests) {
                LoadState.Loading -> LoadingBox(Modifier.height(80.dp))
                is LoadState.Error -> Text(requests.message.asString(), color = MaterialTheme.colorScheme.error)
                is LoadState.Success -> if (requests.data.isEmpty()) {
                    Text(stringResource(R.string.bookings_requests_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    requests.data.forEach { request ->
                        Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(requestTypeLabel(request.type)), fontWeight = FontWeight.SemiBold)
                                StatusChip(stringResource(requestStatusLabel(request.status)), requestStatusColor(request.status))
                            }
                            if (!request.reason.isNullOrBlank()) Text(request.reason, fontSize = 13.sp)
                            if (!request.adminResponse.isNullOrBlank()) {
                                Text(stringResource(R.string.bookings_admin_response, request.adminResponse), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(formatDateTime(request.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            booking.hotel?.let { hotel ->
                TextButton(onClick = {
                    onDismiss()
                    onOpenHotel(hotel.id)
                }) { Text(stringResource(R.string.bookings_view_hotel)) }
            }
        }
    }
}
