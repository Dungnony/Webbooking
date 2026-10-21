package com.nhom2.booking.ui.format

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.nhom2.booking.R
import com.nhom2.booking.data.model.BookingStatus
import com.nhom2.booking.data.model.PaymentStatus
import com.nhom2.booking.data.model.RequestStatus
import com.nhom2.booking.data.model.RequestType
import com.nhom2.booking.domain.RatingLabel
import com.nhom2.booking.domain.parseLocalDate
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToLong

private const val PLACEHOLDER = "—"
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
// Prices are always shown the Vietnamese way ("1.200.000₫"), as on the web, whatever the app language.
private val VND_LOCALE: Locale = Locale.forLanguageTag("vi-VN")

val StatusPending = Color(0xFFD48806)
val StatusSuccess = Color(0xFF389E0D)
val StatusError = Color(0xFFCF1322)
val StatusNeutral = Color(0xFF595959)

fun formatVnd(amount: Double?): String =
    if (amount == null) PLACEHOLDER else NumberFormat.getIntegerInstance(VND_LOCALE).format(amount.roundToLong()) + "₫"

fun formatDate(value: String?): String = parseLocalDate(value)?.format(DATE_FORMAT) ?: PLACEHOLDER

fun formatDate(date: LocalDate?): String = date?.format(DATE_FORMAT) ?: PLACEHOLDER

fun formatDateTime(value: String?): String = try {
    value?.let { LocalDateTime.parse(it).format(DATE_TIME_FORMAT) } ?: PLACEHOLDER
} catch (e: DateTimeParseException) {
    formatDate(value)
}

fun formatRating(score: Double?): String =
    if (score == null || score <= 0) PLACEHOLDER else "%.1f".format(Locale.US, score)

@StringRes
fun RatingLabel.labelRes(): Int = when (this) {
    RatingLabel.EXCEPTIONAL -> R.string.rating_exceptional
    RatingLabel.VERY_GOOD -> R.string.rating_very_good
    RatingLabel.GOOD -> R.string.rating_good
    RatingLabel.OKAY -> R.string.rating_okay
    RatingLabel.AVERAGE -> R.string.rating_average
    RatingLabel.NONE -> R.string.rating_none
}

@StringRes
fun bookingStatusLabel(status: String?): Int = when (status) {
    BookingStatus.CONFIRMED -> R.string.booking_status_confirmed
    BookingStatus.CANCELLED -> R.string.booking_status_cancelled
    BookingStatus.REJECTED -> R.string.booking_status_rejected
    else -> R.string.booking_status_pending
}

fun bookingStatusColor(status: String?): Color = when (status) {
    BookingStatus.CONFIRMED -> StatusSuccess
    BookingStatus.CANCELLED, BookingStatus.REJECTED -> StatusError
    else -> StatusPending
}

@StringRes
fun paymentStatusLabel(status: String?): Int = when (status) {
    PaymentStatus.PAID -> R.string.payment_status_paid
    PaymentStatus.REFUNDED -> R.string.payment_status_refunded
    else -> R.string.payment_status_unpaid
}

@StringRes
fun requestStatusLabel(status: String?): Int = when (status) {
    RequestStatus.APPROVED -> R.string.request_status_approved
    RequestStatus.REJECTED -> R.string.request_status_rejected
    else -> R.string.request_status_pending
}

fun requestStatusColor(status: String?): Color = when (status) {
    RequestStatus.APPROVED -> StatusSuccess
    RequestStatus.REJECTED -> StatusError
    else -> StatusPending
}

@StringRes
fun requestTypeLabel(type: String?): Int =
    if (type == RequestType.CHANGE_DATE) R.string.request_type_change_date else R.string.request_type_cancel
