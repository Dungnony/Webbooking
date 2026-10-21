package com.nhom2.booking.domain

import com.nhom2.booking.data.model.Booking
import com.nhom2.booking.data.model.BookingStatus
import com.nhom2.booking.data.model.PaymentStatus
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Typed on the booking form; the backend does not store it. */
data class GuestDetails(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val specialRequests: String,
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

private val ACTIVE_STATUSES = setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)

fun parseLocalDate(value: String?): LocalDate? = try {
    value?.take(10)?.let(LocalDate::parse)
} catch (e: DateTimeParseException) {
    null
}

/** Nights between two dates, at least one (same rule the web app shows). */
fun nightsBetween(checkIn: LocalDate?, checkOut: LocalDate?): Int {
    if (checkIn == null || checkOut == null) return 1
    return ChronoUnit.DAYS.between(checkIn, checkOut).toInt().coerceAtLeast(1)
}

fun Booking.nights(): Int = nightsBetween(parseLocalDate(checkInDate), parseLocalDate(checkOutDate))

/** Mirrors the backend rule: a booking can only be cancelled before its check-in date. */
fun Booking.isBeforeCheckIn(today: LocalDate): Boolean = parseLocalDate(checkInDate)?.isAfter(today) == true

private fun Booking.isActive() = status in ACTIVE_STATUSES

/** Unpaid bookings are cancelled directly. */
fun Booking.canCancelDirectly(today: LocalDate): Boolean =
    isActive() && paymentStatus != PaymentStatus.PAID && isBeforeCheckIn(today)

/** Paid bookings need an admin to approve the cancellation and refund. */
fun Booking.canRequestCancel(today: LocalDate): Boolean =
    isActive() && paymentStatus == PaymentStatus.PAID && isBeforeCheckIn(today)

fun Booking.canPay(): Boolean = status == BookingStatus.PENDING && paymentStatus != PaymentStatus.PAID

fun Booking.canDownloadInvoice(): Boolean = paymentStatus == PaymentStatus.PAID

fun bookingCode(id: Long): String = "BK%06d".format(id)

// ─── Admin rules (same buttons the web admin shows) ─────────

fun Booking.canAdminDecide(): Boolean = status == BookingStatus.PENDING

fun Booking.canMarkPaid(): Boolean =
    paymentStatus == PaymentStatus.UNPAID && status != BookingStatus.CANCELLED && status != BookingStatus.REJECTED

/** Revenue of paid bookings per month ("2026-10" → amount), oldest first, limited to the last [months]. */
fun monthlyRevenue(bookings: List<Booking>, months: Int): List<Pair<String, Double>> =
    bookings
        .filter { it.paymentStatus == PaymentStatus.PAID && it.createdAt != null }
        .groupBy { it.createdAt!!.take(7) }
        .mapValues { (_, items) -> items.sumOf { it.totalPrice ?: 0.0 } }
        .toSortedMap()
        .entries
        .toList()
        .takeLast(months)
        .map { it.key to it.value }
