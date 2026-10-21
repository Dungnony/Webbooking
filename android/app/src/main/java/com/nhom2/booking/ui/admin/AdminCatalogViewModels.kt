package com.nhom2.booking.ui.admin

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AdminApi
import com.nhom2.booking.data.api.CatalogApi
import com.nhom2.booking.data.catalog.HotelRepository
import com.nhom2.booking.data.files.UnsupportedImageException
import com.nhom2.booking.data.files.imagePart
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.model.HotelBody
import com.nhom2.booking.data.model.Room
import com.nhom2.booking.data.model.RoomBody
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

/** Fields an admin edits for a hotel. */
data class HotelDraft(val name: String, val city: String, val address: String, val description: String) {
    val isComplete: Boolean get() = listOf(name, city, address, description).all { it.isNotBlank() }
}

/** Fields an admin edits for a room; numbers stay text until saved so the fields can be typed freely. */
data class RoomDraft(
    val name: String,
    val type: String,
    val price: String,
    val capacity: String,
    val quantity: String,
    val description: String,
) {
    fun toBody(): RoomBody? {
        val pricePerNight = price.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val guests = capacity.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val count = quantity.toIntOrNull()?.takeIf { it > 0 } ?: return null
        if (name.isBlank() || type.isBlank()) return null
        return RoomBody(name.trim(), type.trim(), pricePerNight, guests, count, description.trim().ifBlank { null })
    }

    companion object {
        val EMPTY = RoomDraft("", "", "", "", "1", "")

        fun from(room: Room) = RoomDraft(
            name = room.name.orEmpty(),
            type = room.type.orEmpty(),
            price = room.pricePerNight?.toLong()?.toString().orEmpty(),
            capacity = room.capacity?.toString().orEmpty(),
            quantity = room.quantity?.toString().orEmpty(),
            description = room.description.orEmpty(),
        )
    }
}

/** Shared plumbing: a busy flag, error handling and image upload for the catalogue screens. */
abstract class AdminCatalogViewModel(private val appContext: Context) : EventViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    protected abstract fun reload()

    protected fun mutate(@StringRes successRes: Int, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
                showMessage(successRes)
                reload()
            } catch (e: ApiException) {
                showError(e)
            } finally {
                _busy.value = false
            }
        }
    }

    protected fun upload(uri: Uri, send: suspend (MultipartBody.Part) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val part = imagePart(appContext, uri)
                apiCall { send(part) }
                showMessage(R.string.admin_image_uploaded)
                reload()
            } catch (e: UnsupportedImageException) {
                showMessage(R.string.image_unsupported)
            } catch (e: ApiException) {
                showError(e, R.string.admin_image_upload_failed)
            } finally {
                _busy.value = false
            }
        }
    }
}

class AdminHotelsViewModel(
    private val adminApi: AdminApi,
    private val hotelRepository: HotelRepository,
    appContext: Context,
) : AdminCatalogViewModel(appContext) {

    private val _hotels = MutableStateFlow<LoadState<List<Hotel>>>(LoadState.Loading)
    val hotels: StateFlow<LoadState<List<Hotel>>> = _hotels.asStateFlow()

    init {
        reload()
    }

    public override fun reload() {
        viewModelScope.launch {
            if (_hotels.value !is LoadState.Success) _hotels.value = LoadState.Loading
            try {
                _hotels.value = LoadState.Success(hotelRepository.getHotels(forceRefresh = true).sortedBy { it.name.orEmpty().lowercase() })
            } catch (e: ApiException) {
                if (_hotels.value is LoadState.Success) {
                    showError(e, R.string.hotels_load_failed)
                } else {
                    _hotels.value = LoadState.Error(e.toUiText(UiText.Res(R.string.hotels_load_failed)))
                }
            }
        }
    }

    /** [existing] is null for a new hotel. */
    fun save(existing: Hotel?, draft: HotelDraft) = mutate(R.string.admin_hotel_saved) {
        val body = HotelBody(
            id = existing?.id,
            name = draft.name.trim(),
            address = draft.address.trim(),
            city = draft.city.trim(),
            description = draft.description.trim(),
            // The backend overwrites the whole hotel, so untouched fields are sent back as they were.
            imageUrl = existing?.imageUrl,
            ratingAvg = existing?.ratingAvg,
            status = existing?.status,
            createdAt = existing?.createdAt,
        )
        apiCall { if (existing == null) adminApi.createHotel(body) else adminApi.updateHotel(existing.id, body) }
        hotelRepository.invalidate()
    }

    fun delete(hotel: Hotel) = mutate(R.string.admin_hotel_deleted) {
        apiCall { adminApi.deleteHotel(hotel.id) }
        hotelRepository.invalidate()
    }

    fun uploadImage(hotel: Hotel, uri: Uri) = upload(uri) {
        adminApi.uploadHotelImage(hotel.id, it)
        hotelRepository.invalidate()
    }
}

class AdminRoomsViewModel(
    private val hotelId: Long,
    private val adminApi: AdminApi,
    private val catalogApi: CatalogApi,
    private val hotelRepository: HotelRepository,
    appContext: Context,
) : AdminCatalogViewModel(appContext) {

    private val _rooms = MutableStateFlow<LoadState<List<Room>>>(LoadState.Loading)
    val rooms: StateFlow<LoadState<List<Room>>> = _rooms.asStateFlow()

    init {
        reload()
    }

    public override fun reload() {
        viewModelScope.launch {
            if (_rooms.value !is LoadState.Success) _rooms.value = LoadState.Loading
            try {
                _rooms.value = LoadState.Success(apiCall { catalogApi.getRooms(hotelId) }.sortedBy { it.id })
            } catch (e: ApiException) {
                if (_rooms.value is LoadState.Success) {
                    showError(e, R.string.admin_rooms_load_failed)
                } else {
                    _rooms.value = LoadState.Error(e.toUiText(UiText.Res(R.string.admin_rooms_load_failed)))
                }
            }
        }
    }

    /** Returns false (and saves nothing) when the draft has missing or invalid fields. */
    fun save(existing: Room?, draft: RoomDraft): Boolean {
        val body = draft.toBody() ?: return false
        mutate(R.string.admin_room_saved) {
            apiCall { if (existing == null) adminApi.createRoom(hotelId, body) else adminApi.updateRoom(existing.id, body) }
            hotelRepository.invalidate()
        }
        return true
    }

    fun delete(room: Room) = mutate(R.string.admin_room_deleted) {
        apiCall { adminApi.deleteRoom(room.id) }
        hotelRepository.invalidate()
    }

    fun uploadImage(room: Room, uri: Uri) = upload(uri) {
        adminApi.uploadRoomImage(room.id, it)
        hotelRepository.invalidate()
    }
}
