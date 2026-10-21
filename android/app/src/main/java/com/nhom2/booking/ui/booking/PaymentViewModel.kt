package com.nhom2.booking.ui.booking

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.BookingApi
import com.nhom2.booking.data.model.Booking
import com.nhom2.booking.data.model.PaymentQr
import com.nhom2.booking.data.model.PaymentStatus
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PaymentViewModel"
private const val PAYMENT_POLL_INTERVAL_MS = 3_000L
// Must match BookingExpiryService.PAYMENT_WINDOW on the backend.
const val PAYMENT_WINDOW_MINUTES = 30

/** Loads a booking of the signed-in user; the backend has no single-booking endpoint for guests. */
suspend fun BookingApi.findMine(bookingId: Long): Booking =
    apiCall { getMine() }.firstOrNull { it.id == bookingId }
        ?: throw ApiException.UnexpectedResponse("Booking $bookingId not found")

class PaymentViewModel(
    private val bookingId: Long,
    private val bookingApi: BookingApi,
) : EventViewModel() {

    private val _booking = MutableStateFlow<LoadState<Booking>>(LoadState.Loading)
    val booking: StateFlow<LoadState<Booking>> = _booking.asStateFlow()

    private val _qr = MutableStateFlow<LoadState<PaymentQr>>(LoadState.Loading)
    val qr: StateFlow<LoadState<PaymentQr>> = _qr.asStateFlow()

    private val _confirming = MutableStateFlow(false)
    val confirming: StateFlow<Boolean> = _confirming.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _booking.value = try {
                LoadState.Success(bookingApi.findMine(bookingId))
            } catch (e: ApiException) {
                LoadState.Error(e.toUiText(UiText.Res(R.string.booking_load_failed)))
            }
        }
        viewModelScope.launch {
            _qr.value = LoadState.Loading
            _qr.value = try {
                LoadState.Success(apiCall { bookingApi.getPaymentQr(bookingId) })
            } catch (e: ApiException) {
                LoadState.Error(e.toUiText(UiText.Res(R.string.payment_qr_unavailable)))
            }
        }
    }

    /**
     * Checks every few seconds whether the bank transfer arrived (the Sepay webhook marks the booking paid).
     * Runs only while the screen is visible; returns once the booking is paid.
     */
    suspend fun waitForPayment() {
        while (true) {
            delay(PAYMENT_POLL_INTERVAL_MS)
            try {
                val updated = bookingApi.findMine(bookingId)
                _booking.value = LoadState.Success(updated)
                if (updated.paymentStatus == PaymentStatus.PAID) {
                    showMessage(R.string.payment_paid_automatically)
                    complete()
                    return
                }
            } catch (e: ApiException) {
                Log.w(TAG, "Payment status poll failed, retrying", e)
            }
        }
    }

    /** The guest says they transferred; an admin then checks it. */
    fun confirmTransfer() {
        if (_confirming.value) return
        _confirming.value = true
        viewModelScope.launch {
            try {
                apiCall { bookingApi.confirmPayment(bookingId) }
                showMessage(R.string.payment_confirmation_sent)
                complete()
            } catch (e: ApiException) {
                showError(e, R.string.payment_confirm_failed)
            } finally {
                _confirming.value = false
            }
        }
    }
}
