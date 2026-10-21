package com.nhom2.booking.ui.bookings

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.BookingApi
import com.nhom2.booking.data.files.MIME_PDF
import com.nhom2.booking.data.files.openDocument
import com.nhom2.booking.data.files.saveToCache
import com.nhom2.booking.data.model.Booking
import com.nhom2.booking.data.model.BookingRequestItem
import com.nhom2.booking.data.model.CreateBookingRequestBody
import com.nhom2.booking.data.model.RequestType
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.domain.bookingCode
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

data class BookingDetails(val booking: Booking, val requests: LoadState<List<BookingRequestItem>>)

class MyBookingsViewModel(
    private val bookingApi: BookingApi,
    private val appContext: Context,
) : EventViewModel() {

    private val allBookings = MutableStateFlow<LoadState<List<Booking>>>(LoadState.Loading)

    /** Null shows every booking. */
    private val _statusFilter = MutableStateFlow<String?>(null)
    val statusFilter: StateFlow<String?> = _statusFilter.asStateFlow()

    val bookings: StateFlow<LoadState<List<Booking>>> =
        combine(allBookings, _statusFilter) { state, filter ->
            when (state) {
                is LoadState.Success -> LoadState.Success(
                    state.data.filter { filter == null || it.status == filter }.sortedByDescending { it.id },
                )
                else -> state
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Booking whose action is running, so its buttons can be disabled. */
    private val _busyBookingId = MutableStateFlow<Long?>(null)
    val busyBookingId: StateFlow<Long?> = _busyBookingId.asStateFlow()

    private val _details = MutableStateFlow<BookingDetails?>(null)
    val details: StateFlow<BookingDetails?> = _details.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (allBookings.value is LoadState.Success) _refreshing.value = true else allBookings.value = LoadState.Loading
            try {
                allBookings.value = LoadState.Success(apiCall { bookingApi.getMine() })
            } catch (e: ApiException) {
                if (allBookings.value is LoadState.Success) {
                    showError(e, R.string.bookings_load_failed)
                } else {
                    allBookings.value = LoadState.Error(e.toUiText(UiText.Res(R.string.bookings_load_failed)))
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun setStatusFilter(status: String?) {
        _statusFilter.value = status
    }

    fun cancel(bookingId: Long) = runAction(bookingId) {
        apiCall { bookingApi.cancel(bookingId) }
        showMessage(R.string.bookings_cancelled)
        load()
    }

    /** Paid bookings cannot be cancelled directly: an admin approves the refund. */
    fun requestCancel(bookingId: Long, reason: String) = runAction(bookingId) {
        val defaultReason = appContext.getString(R.string.bookings_cancel_request_default_reason)
        apiCall {
            bookingApi.createRequest(
                bookingId,
                CreateBookingRequestBody(type = RequestType.CANCEL, reason = reason.ifBlank { defaultReason }),
            )
        }
        showMessage(R.string.bookings_cancel_requested)
    }

    fun downloadInvoice(bookingId: Long) = runAction(bookingId) {
        val body = apiCall { bookingApi.downloadInvoice(bookingId) }
        try {
            val file = saveToCache(appContext, body, "${bookingCode(bookingId)}.pdf")
            if (!openDocument(appContext, file, MIME_PDF)) showMessage(R.string.no_app_to_open_file)
        } catch (e: IOException) {
            showMessage(R.string.download_failed)
        }
    }

    fun showDetails(booking: Booking) {
        _details.value = BookingDetails(booking, LoadState.Loading)
        viewModelScope.launch {
            val requests = try {
                LoadState.Success(apiCall { bookingApi.getRequests(booking.id) })
            } catch (e: ApiException) {
                LoadState.Error(e.toUiText(UiText.Res(R.string.bookings_requests_load_failed)))
            }
            _details.value = _details.value?.takeIf { it.booking.id == booking.id }?.copy(requests = requests)
        }
    }

    fun hideDetails() {
        _details.value = null
    }

    private fun runAction(bookingId: Long, action: suspend () -> Unit) {
        if (_busyBookingId.value != null) return
        _busyBookingId.value = bookingId
        viewModelScope.launch {
            try {
                action()
            } catch (e: ApiException) {
                showError(e)
            } finally {
                _busyBookingId.value = null
            }
        }
    }
}
