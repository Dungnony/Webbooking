package com.nhom2.booking.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom2.booking.R
import com.nhom2.booking.domain.MAX_GUESTS
import com.nhom2.booking.domain.MAX_ROOMS
import com.nhom2.booking.domain.MIN_GUESTS
import com.nhom2.booking.domain.MIN_ROOMS
import com.nhom2.booking.domain.nightsBetween
import com.nhom2.booking.ui.format.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Material date pickers work in UTC milliseconds at midnight.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Read-only field that opens a dialog when tapped. */
@Composable
fun PickerField(icon: ImageVector, label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun StayDatesField(
    checkIn: LocalDate,
    checkOut: LocalDate,
    onDatesChange: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val nights = nightsBetween(checkIn, checkOut)
    PickerField(
        icon = Icons.Filled.CalendarMonth,
        label = stringResource(R.string.stay_dates_label),
        value = "${formatDate(checkIn)} → ${formatDate(checkOut)} · " +
            pluralStringResource(R.plurals.nights, nights, nights),
        onClick = { showPicker = true },
        modifier = modifier,
    )
    if (showPicker) {
        StayDatesDialog(
            checkIn = checkIn,
            checkOut = checkOut,
            onConfirm = { start, end ->
                onDatesChange(start, end)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StayDatesDialog(
    checkIn: LocalDate,
    checkOut: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = remember { LocalDate.now().toUtcMillis() }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = checkIn.toUtcMillis(),
        initialSelectedEndDateMillis = checkOut.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
        },
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(start!!.toUtcDate(), end!!.toUtcDate()) },
                enabled = start != null && end != null && end > start,
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text(stringResource(R.string.stay_dates_label), modifier = Modifier.padding(16.dp)) },
            modifier = Modifier.height(500.dp),
        )
    }
}

@Composable
fun GuestsRoomsField(
    guests: Int,
    rooms: Int,
    onChange: (guests: Int, rooms: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    PickerField(
        icon = Icons.Filled.Person,
        label = stringResource(R.string.guests_rooms_label),
        value = pluralStringResource(R.plurals.guests, guests, guests) + " · " +
            pluralStringResource(R.plurals.rooms, rooms, rooms),
        onClick = { showDialog = true },
        modifier = modifier,
    )
    if (showDialog) {
        var draftGuests by remember { mutableIntStateOf(guests) }
        var draftRooms by remember { mutableIntStateOf(rooms) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.guests_rooms_label)) },
            text = {
                Column {
                    Stepper(stringResource(R.string.guests_label), draftGuests, MIN_GUESTS, MAX_GUESTS) { draftGuests = it }
                    HorizontalDivider()
                    Stepper(stringResource(R.string.rooms_label), draftRooms, MIN_ROOMS, MAX_ROOMS) { draftRooms = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onChange(draftGuests, draftRooms)
                    showDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onChange(value - 1) }, enabled = value > min) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.decrease))
        }
        Text(value.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase))
        }
    }
}
