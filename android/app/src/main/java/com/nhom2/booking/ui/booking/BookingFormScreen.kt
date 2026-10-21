package com.nhom2.booking.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom2.booking.R
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.model.Room
import com.nhom2.booking.domain.sortRoomsByPrice
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.FormTextField
import com.nhom2.booking.ui.components.InfoRow
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.LoadingButton
import com.nhom2.booking.ui.components.SectionCard
import com.nhom2.booking.ui.components.StayDatesField
import com.nhom2.booking.ui.format.formatVnd

@Composable
fun BookingFormScreen(
    viewModel: BookingFormViewModel,
    onMessage: (UiText) -> Unit,
    onBooked: (bookingId: Long) -> Unit,
    onBack: () -> Unit,
) {
    val hotelState by viewModel.hotel.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val createdBookingId by viewModel.createdBookingId.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})
    LaunchedEffect(createdBookingId) { createdBookingId?.let(onBooked) }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.booking_title), onBack = onBack) }) { padding ->
        when (val state = hotelState) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> ErrorBox(state.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is LoadState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StepIndicator(current = 0)
                Text(state.data.name ?: stringResource(R.string.hotel_fallback_name), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)

                SectionCard {
                    Text(stringResource(R.string.booking_stay_section), fontWeight = FontWeight.Bold)
                    StayDatesField(form.checkIn, form.checkOut, viewModel::changeDates)
                }

                RoomSection(state.data, form, viewModel)

                SectionCard {
                    Text(stringResource(R.string.booking_guest_section), fontWeight = FontWeight.Bold)
                    FormTextField(form.lastName, viewModel::onLastNameChange, stringResource(R.string.booking_last_name), form.lastNameError, leadingIcon = Icons.Filled.Person)
                    FormTextField(form.firstName, viewModel::onFirstNameChange, stringResource(R.string.booking_first_name), form.firstNameError)
                    FormTextField(form.email, viewModel::onEmailChange, stringResource(R.string.auth_email), form.emailError, leadingIcon = Icons.Filled.Email, keyboardType = KeyboardType.Email)
                    FormTextField(form.phone, viewModel::onPhoneChange, stringResource(R.string.booking_phone), form.phoneError, leadingIcon = Icons.Filled.Phone, keyboardType = KeyboardType.Phone)
                    OutlinedTextField(
                        value = form.specialRequests,
                        onValueChange = viewModel::onSpecialRequestsChange,
                        label = { Text(stringResource(R.string.booking_special_requests)) },
                        minLines = 2,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Summary(state.data, form)
                LoadingButton(stringResource(R.string.booking_confirm), form.submitting, viewModel::submit)
            }
        }
    }
}

@Composable
private fun RoomSection(hotel: Hotel, form: BookingFormState, viewModel: BookingFormViewModel) {
    SectionCard {
        Text(stringResource(R.string.booking_room_section), fontWeight = FontWeight.Bold)
        sortRoomsByPrice(hotel.rooms).forEach { room ->
            RoomOption(room, selected = room.id == form.selectedRoomId, onSelect = { viewModel.selectRoom(room) })
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.booking_room_quantity), modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.changeQuantity(form.quantity - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.decrease))
            }
            Text(form.quantity.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = { viewModel.changeQuantity(form.quantity + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase))
            }
        }
    }
}

@Composable
private fun RoomOption(room: Room, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(room.name ?: room.type.orEmpty(), fontWeight = FontWeight.SemiBold)
            room.capacity?.let {
                Text(pluralStringResource(R.plurals.guests, it, it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(formatVnd(room.pricePerNight), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Summary(hotel: Hotel, form: BookingFormState) {
    val room = hotel.rooms.firstOrNull { it.id == form.selectedRoomId }
    val pricePerNight = room?.pricePerNight ?: 0.0
    SectionCard {
        Text(stringResource(R.string.booking_summary), fontWeight = FontWeight.Bold)
        InfoRow(stringResource(R.string.booking_price_per_night), formatVnd(pricePerNight))
        InfoRow(stringResource(R.string.booking_nights_label), pluralStringResource(R.plurals.nights, form.nights, form.nights))
        InfoRow(stringResource(R.string.booking_room_quantity), form.quantity.toString())
        HorizontalDivider()
        // Indicative only: the backend computes the amount actually charged.
        InfoRow(stringResource(R.string.booking_total), formatVnd(pricePerNight * form.nights * form.quantity), emphasize = true)
        Text(stringResource(R.string.booking_perks), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Guest details → Payment → Confirmation" progress, as on the web booking pages. */
@Composable
fun StepIndicator(current: Int) {
    val steps = listOf(
        stringResource(R.string.booking_step_details),
        stringResource(R.string.booking_step_payment),
        stringResource(R.string.booking_step_confirmation),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, label ->
            val color = if (index <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                "${index + 1}. $label",
                color = color,
                fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
            )
            if (index < steps.lastIndex) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
