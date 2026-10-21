package com.nhom2.booking.ui.booking

import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AccountApi
import com.nhom2.booking.data.api.BookingApi
import com.nhom2.booking.data.catalog.HotelRepository
import com.nhom2.booking.data.model.BookingRoomBody
import com.nhom2.booking.data.model.CreateBookingBody
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.model.Room
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.domain.GuestDetails
import com.nhom2.booking.domain.StayQuery
import com.nhom2.booking.domain.isValidEmail
import com.nhom2.booking.domain.isValidPhone
import com.nhom2.booking.domain.isValidStay
import com.nhom2.booking.domain.nightsBetween
import com.nhom2.booking.domain.sortRoomsByPrice
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BookingFormState(
    val selectedRoomId: Long? = null,
    val quantity: Int = 1,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val specialRequests: String = "",
    val firstNameError: UiText? = null,
    val lastNameError: UiText? = null,
    val emailError: UiText? = null,
    val phoneError: UiText? = null,
    val submitting: Boolean = false,
) {
    val nights: Int get() = nightsBetween(checkIn, checkOut)
}

class BookingFormViewModel(
    private val hotelId: Long,
    preselectedRoomId: Long?,
    private val hotelRepository: HotelRepository,
    private val bookingApi: BookingApi,
    private val accountApi: AccountApi,
    private val stayQuery: MutableStateFlow<StayQuery>,
    private val guestDetailsByBooking: MutableMap<Long, GuestDetails>,
    private val today: () -> LocalDate = LocalDate::now,
) : EventViewModel() {

    private val _hotel = MutableStateFlow<LoadState<Hotel>>(LoadState.Loading)
    val hotel: StateFlow<LoadState<Hotel>> = _hotel.asStateFlow()

    private val _form = MutableStateFlow(
        BookingFormState(
            selectedRoomId = preselectedRoomId,
            quantity = stayQuery.value.rooms,
            checkIn = stayQuery.value.checkIn,
            checkOut = stayQuery.value.checkOut,
        ),
    )
    val form: StateFlow<BookingFormState> = _form.asStateFlow()

    /** Set to the new booking's id once it is created; the screen then opens the payment step. */
    private val _createdBookingId = MutableStateFlow<Long?>(null)
    val createdBookingId: StateFlow<Long?> = _createdBookingId.asStateFlow()

    init {
        load()
        prefillEmail()
    }

    fun load() {
        viewModelScope.launch {
            _hotel.value = LoadState.Loading
            _hotel.value = try {
                val hotel = hotelRepository.getHotel(hotelId)
                // Without a room picked on the hotel page, start with the cheapest one, as the web form does.
                _form.update { form ->
                    val roomId = form.selectedRoomId?.takeIf { id -> hotel.rooms.any { it.id == id } }
                        ?: sortRoomsByPrice(hotel.rooms).firstOrNull()?.id
                    form.copy(selectedRoomId = roomId, quantity = clampQuantity(form.quantity, hotel.room(roomId)))
                }
                LoadState.Success(hotel)
            } catch (e: ApiException) {
                LoadState.Error(e.toUiText(UiText.Res(R.string.hotel_load_failed)))
            }
        }
    }

    private fun prefillEmail() {
        viewModelScope.launch {
            try {
                val email = apiCall { accountApi.getMe() }.email.orEmpty()
                _form.update { if (it.email.isBlank()) it.copy(email = email) else it }
            } catch (e: ApiException) {
                // Only a convenience: the guest can still type the address.
            }
        }
    }

    fun selectRoom(room: Room) = _form.update {
        it.copy(selectedRoomId = room.id, quantity = clampQuantity(it.quantity, room))
    }

    fun changeQuantity(quantity: Int) = _form.update { form ->
        form.copy(quantity = clampQuantity(quantity, (hotel.value as? LoadState.Success)?.data?.room(form.selectedRoomId)))
    }

    fun changeDates(checkIn: LocalDate, checkOut: LocalDate) {
        _form.update { it.copy(checkIn = checkIn, checkOut = checkOut) }
        stayQuery.update { it.copy(checkIn = checkIn, checkOut = checkOut) }
    }

    fun onFirstNameChange(value: String) = _form.update { it.copy(firstName = value, firstNameError = null) }
    fun onLastNameChange(value: String) = _form.update { it.copy(lastName = value, lastNameError = null) }
    fun onEmailChange(value: String) = _form.update { it.copy(email = value, emailError = null) }
    fun onPhoneChange(value: String) = _form.update { it.copy(phone = value, phoneError = null) }
    fun onSpecialRequestsChange(value: String) = _form.update { it.copy(specialRequests = value) }

    fun submit() {
        val current = _form.value
        if (current.submitting) return
        val validated = current.copy(
            firstNameError = if (current.firstName.isBlank()) UiText.Res(R.string.booking_first_name_required) else null,
            lastNameError = if (current.lastName.isBlank()) UiText.Res(R.string.booking_last_name_required) else null,
            emailError = if (isValidEmail(current.email)) null else UiText.Res(R.string.auth_email_invalid),
            phoneError = if (isValidPhone(current.phone)) null else UiText.Res(R.string.booking_phone_invalid),
        )
        _form.value = validated
        if (listOf(validated.firstNameError, validated.lastNameError, validated.emailError, validated.phoneError).any { it != null }) {
            return
        }
        if (!isValidStay(current.checkIn, current.checkOut, today())) {
            showMessage(R.string.booking_pick_dates)
            return
        }
        val roomId = current.selectedRoomId
        if (roomId == null) {
            showMessage(R.string.booking_room_missing)
            return
        }

        _form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                val booking = apiCall {
                    bookingApi.create(
                        hotelId,
                        CreateBookingBody(
                            checkInDate = current.checkIn.toString(),
                            checkOutDate = current.checkOut.toString(),
                            rooms = listOf(BookingRoomBody(roomId, current.quantity)),
                        ),
                    )
                }
                guestDetailsByBooking[booking.id] = GuestDetails(
                    firstName = current.firstName.trim(),
                    lastName = current.lastName.trim(),
                    email = current.email.trim(),
                    phone = current.phone.trim(),
                    specialRequests = current.specialRequests.trim(),
                )
                _createdBookingId.value = booking.id
            } catch (e: ApiException) {
                showError(e, R.string.booking_create_failed)
            } finally {
                _form.update { it.copy(submitting = false) }
            }
        }
    }

    private fun Hotel.room(id: Long?): Room? = rooms.firstOrNull { it.id == id }

    private fun clampQuantity(quantity: Int, room: Room?): Int =
        quantity.coerceIn(1, (room?.quantity ?: 1).coerceAtLeast(1))
}
