package com.nhom2.booking.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.BookingApi
import com.nhom2.booking.data.model.Booking
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.domain.GuestDetails
import com.nhom2.booking.domain.bookingCode
import com.nhom2.booking.domain.nights
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.InfoRow
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.LoadingButton
import com.nhom2.booking.ui.components.SecondaryButton
import com.nhom2.booking.ui.components.SectionCard
import com.nhom2.booking.ui.format.formatDate
import com.nhom2.booking.ui.format.formatVnd
import com.nhom2.booking.ui.format.paymentStatusLabel
import com.nhom2.booking.ui.theme.SuccessGreen
import com.nhom2.booking.ui.theme.SuccessGreenContainer
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConfirmationViewModel(
    private val bookingId: Long,
    private val bookingApi: BookingApi,
    val guestDetails: GuestDetails?,
) : ViewModel() {

    private val _booking = MutableStateFlow<LoadState<Booking>>(LoadState.Loading)
    val booking: StateFlow<LoadState<Booking>> = _booking.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _booking.value = LoadState.Loading
            _booking.value = try {
                LoadState.Success(bookingApi.findMine(bookingId))
            } catch (e: ApiException) {
                LoadState.Error(e.toUiText(UiText.Res(R.string.booking_load_failed)))
            }
        }
    }
}

@Composable
fun ConfirmationScreen(
    viewModel: ConfirmationViewModel,
    onViewBookings: () -> Unit,
    onHome: () -> Unit,
) {
    val state by viewModel.booking.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar(stringResource(R.string.confirmation_title)) }) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is LoadState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StepIndicator(current = 2)
                Header(current.data)
                Details(current.data, viewModel.guestDetails)
                Notes()
                LoadingButton(stringResource(R.string.confirmation_view_bookings), loading = false, onClick = onViewBookings)
                SecondaryButton(stringResource(R.string.confirmation_home), onHome, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Header(booking: Booking) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(SuccessGreenContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(44.dp))
        }
        Text(stringResource(R.string.confirmation_heading), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.confirmation_thanks), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.booking_code) + ": " + bookingCode(booking.id), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Details(booking: Booking, guest: GuestDetails?) {
    val nights = booking.nights()
    val room = booking.bookingRooms.firstOrNull()?.room
    SectionCard {
        Text(booking.hotel?.name ?: stringResource(R.string.hotel_fallback_name), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        if (guest != null) {
            InfoRow(stringResource(R.string.confirmation_guest), guest.fullName)
            InfoRow(stringResource(R.string.booking_phone), guest.phone)
            InfoRow(stringResource(R.string.auth_email), guest.email)
        }
        if (room != null) InfoRow(stringResource(R.string.confirmation_room), room.name ?: room.type.orEmpty())
        InfoRow(stringResource(R.string.booking_check_in), stringResource(R.string.confirmation_check_in_value, formatDate(booking.checkInDate)))
        InfoRow(stringResource(R.string.booking_check_out), stringResource(R.string.confirmation_check_out_value, formatDate(booking.checkOutDate)))
        InfoRow(stringResource(R.string.booking_nights_label), pluralStringResource(R.plurals.nights, nights, nights))
        InfoRow(stringResource(R.string.booking_payment), stringResource(paymentStatusLabel(booking.paymentStatus)))
        HorizontalDivider()
        InfoRow(stringResource(R.string.booking_total), formatVnd(booking.totalPrice), emphasize = true)
    }
}

@Composable
private fun Notes() {
    SectionCard {
        Text(stringResource(R.string.confirmation_notes_title), fontWeight = FontWeight.Bold)
        Text("• " + stringResource(R.string.confirmation_note_id), fontSize = 13.sp)
        Text("• " + stringResource(R.string.confirmation_note_times), fontSize = 13.sp)
        Text("• " + stringResource(R.string.confirmation_note_cancel), fontSize = 13.sp)
    }
}
