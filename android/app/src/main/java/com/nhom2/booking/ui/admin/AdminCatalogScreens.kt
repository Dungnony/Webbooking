package com.nhom2.booking.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom2.booking.BuildConfig
import com.nhom2.booking.R
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.model.Room
import com.nhom2.booking.domain.coverImage
import com.nhom2.booking.domain.resolveImageUrl
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ConfirmDialog
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.RemoteImage
import com.nhom2.booking.ui.components.SectionCard
import com.nhom2.booking.ui.format.formatVnd

/** Opens the photo picker and hands the chosen image to [onPicked]. */
@Composable
private fun rememberImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

// ─── Hotels ──────────────────────────────────────────────────

@Composable
fun AdminHotelsScreen(viewModel: AdminHotelsViewModel, onMessage: (UiText) -> Unit, onManageRooms: (Hotel) -> Unit) {
    val state by viewModel.hotels.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Hotel?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Hotel?>(null) }
    var uploadTarget by remember { mutableStateOf<Hotel?>(null) }
    val pickImage = rememberImagePicker { uri -> uploadTarget?.let { viewModel.uploadImage(it, uri) } }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.admin_hotels_title)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.admin_add_hotel)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when (val current = state) {
                LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::reload)
                is LoadState.Success -> if (current.data.isEmpty()) {
                    EmptyState(Icons.Filled.Apartment, stringResource(R.string.admin_list_empty), body = null)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(current.data, key = { it.id }) { hotel ->
                            AdminHotelCard(
                                hotel = hotel,
                                enabled = !busy,
                                onEdit = { editing = hotel },
                                onRooms = { onManageRooms(hotel) },
                                onUploadImage = {
                                    uploadTarget = hotel
                                    pickImage()
                                },
                                onDelete = { deleting = hotel },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        HotelDialog(
            hotel = editing,
            onSave = { draft ->
                viewModel.save(editing, draft)
                creating = false
                editing = null
            },
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }
    deleting?.let { hotel ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.admin_delete_hotel_confirm, hotel.name.orEmpty()),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.delete(hotel)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminHotelCard(
    hotel: Hotel,
    enabled: Boolean,
    onEdit: () -> Unit,
    onRooms: () -> Unit,
    onUploadImage: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteImage(hotel.coverImage(BuildConfig.API_BASE_URL), modifier = Modifier.size(72.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(hotel.name.orEmpty(), fontWeight = FontWeight.Bold)
                Text(listOfNotNull(hotel.address, hotel.city).joinToString(", "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pluralStringResource(R.plurals.room_type_count, hotel.rooms.size, hotel.rooms.size), fontSize = 13.sp)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, enabled = enabled) { Text(stringResource(R.string.edit)) }
            OutlinedButton(onClick = onRooms, enabled = enabled) { Text(stringResource(R.string.admin_manage_rooms)) }
            OutlinedButton(onClick = onUploadImage, enabled = enabled) { Text(stringResource(R.string.admin_upload_image)) }
            TextButton(onClick = onDelete, enabled = enabled) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun HotelDialog(hotel: Hotel?, onSave: (HotelDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember {
        mutableStateOf(HotelDraft(hotel?.name.orEmpty(), hotel?.city.orEmpty(), hotel?.address.orEmpty(), hotel?.description.orEmpty()))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (hotel == null) R.string.admin_add_hotel else R.string.admin_edit_hotel)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogField(draft.name, { draft = draft.copy(name = it) }, stringResource(R.string.admin_hotel_name))
                DialogField(draft.city, { draft = draft.copy(city = it) }, stringResource(R.string.admin_hotel_city))
                DialogField(draft.address, { draft = draft.copy(address = it) }, stringResource(R.string.admin_hotel_address))
                DialogField(draft.description, { draft = draft.copy(description = it) }, stringResource(R.string.admin_hotel_description), singleLine = false)
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }, enabled = draft.isComplete) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ─── Rooms ───────────────────────────────────────────────────

@Composable
fun AdminRoomsScreen(viewModel: AdminRoomsViewModel, hotelName: String, onMessage: (UiText) -> Unit, onBack: () -> Unit) {
    val state by viewModel.rooms.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Room?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Room?>(null) }
    var uploadTarget by remember { mutableStateOf<Room?>(null) }
    val pickImage = rememberImagePicker { uri -> uploadTarget?.let { viewModel.uploadImage(it, uri) } }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.admin_rooms_title, hotelName), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.admin_add_room)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when (val current = state) {
                LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::reload)
                is LoadState.Success -> if (current.data.isEmpty()) {
                    EmptyState(Icons.Filled.Bed, stringResource(R.string.admin_list_empty), body = null)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(current.data, key = { it.id }) { room ->
                            AdminRoomCard(
                                room = room,
                                enabled = !busy,
                                onEdit = { editing = room },
                                onUploadImage = {
                                    uploadTarget = room
                                    pickImage()
                                },
                                onDelete = { deleting = room },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        RoomDialog(
            room = editing,
            onSave = { draft ->
                if (viewModel.save(editing, draft)) {
                    creating = false
                    editing = null
                }
            },
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }
    deleting?.let { room ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.admin_delete_room_confirm, room.name.orEmpty()),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.delete(room)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminRoomCard(room: Room, enabled: Boolean, onEdit: () -> Unit, onUploadImage: () -> Unit, onDelete: () -> Unit) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(resolveImageUrl(room.images.firstOrNull()?.imageUrl, BuildConfig.API_BASE_URL), modifier = Modifier.size(64.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(room.name.orEmpty(), fontWeight = FontWeight.Bold)
                Text(room.type.orEmpty(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.admin_room_summary, formatVnd(room.pricePerNight), room.capacity ?: 0, room.quantity ?: 0),
                    fontSize = 13.sp,
                )
                Text(pluralStringResource(R.plurals.photo_count, room.images.size, room.images.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, enabled = enabled) { Text(stringResource(R.string.edit)) }
            OutlinedButton(onClick = onUploadImage, enabled = enabled) { Text(stringResource(R.string.admin_upload_image)) }
            TextButton(onClick = onDelete, enabled = enabled) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RoomDialog(room: Room?, onSave: (RoomDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(room?.let(RoomDraft::from) ?: RoomDraft.EMPTY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (room == null) R.string.admin_add_room else R.string.admin_edit_room)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogField(draft.name, { draft = draft.copy(name = it) }, stringResource(R.string.admin_room_name))
                DialogField(draft.type, { draft = draft.copy(type = it) }, stringResource(R.string.admin_room_type))
                DialogField(draft.price, { draft = draft.copy(price = it.filter(Char::isDigit)) }, stringResource(R.string.admin_room_price), keyboardType = KeyboardType.Number)
                DialogField(draft.capacity, { draft = draft.copy(capacity = it.filter(Char::isDigit)) }, stringResource(R.string.admin_room_capacity), keyboardType = KeyboardType.Number)
                DialogField(draft.quantity, { draft = draft.copy(quantity = it.filter(Char::isDigit)) }, stringResource(R.string.admin_room_quantity), keyboardType = KeyboardType.Number)
                DialogField(draft.description, { draft = draft.copy(description = it) }, stringResource(R.string.admin_room_description), singleLine = false)
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }, enabled = draft.toBody() != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
