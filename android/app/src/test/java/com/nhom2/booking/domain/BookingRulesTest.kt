package com.nhom2.booking.domain

import com.nhom2.booking.data.model.Booking
import com.nhom2.booking.data.model.BookingStatus
import com.nhom2.booking.data.model.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BookingRulesTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun booking(
        status: String = BookingStatus.PENDING,
        payment: String = PaymentStatus.UNPAID,
        checkIn: String? = "2026-10-25",
        checkOut: String? = "2026-10-27",
        createdAt: String? = null,
        total: Double? = null,
    ) = Booking(
        id = 1,
        status = status,
        paymentStatus = payment,
        checkInDate = checkIn,
        checkOutDate = checkOut,
        createdAt = createdAt,
        totalPrice = total,
    )

    @Test
    fun unpaidActiveBookingsBeforeCheckInCanBeCancelledDirectly() {
        assertTrue(booking().canCancelDirectly(today))
        assertTrue(booking(status = BookingStatus.CONFIRMED).canCancelDirectly(today))
        assertFalse(booking().canRequestCancel(today))
    }

    @Test
    fun paidBookingsNeedARefundRequestInstead() {
        val paid = booking(status = BookingStatus.CONFIRMED, payment = PaymentStatus.PAID)

        assertFalse(paid.canCancelDirectly(today))
        assertTrue(paid.canRequestCancel(today))
    }

    @Test
    fun nothingCanBeCancelledFromTheCheckInDayOn() {
        assertFalse(booking(checkIn = "2026-10-21").canCancelDirectly(today))
        assertFalse(booking(checkIn = "2026-10-20").canCancelDirectly(today))
        assertFalse(booking(payment = PaymentStatus.PAID, checkIn = "2026-10-21").canRequestCancel(today))
        assertFalse(booking(checkIn = null).canCancelDirectly(today))
    }

    @Test
    fun closedBookingsOfferNoCancellation() {
        assertFalse(booking(status = BookingStatus.CANCELLED).canCancelDirectly(today))
        assertFalse(booking(status = BookingStatus.REJECTED, payment = PaymentStatus.PAID).canRequestCancel(today))
    }

    @Test
    fun onlyPendingUnpaidBookingsCanBePaid() {
        assertTrue(booking().canPay())
        assertFalse(booking(payment = PaymentStatus.PAID).canPay())
        assertFalse(booking(status = BookingStatus.CONFIRMED).canPay())
        assertTrue(booking(payment = PaymentStatus.PAID).canDownloadInvoice())
        assertFalse(booking(payment = PaymentStatus.REFUNDED).canDownloadInvoice())
    }

    @Test
    fun adminButtonsFollowTheWebRules() {
        assertTrue(booking().canAdminDecide())
        assertFalse(booking(status = BookingStatus.CONFIRMED).canAdminDecide())
        assertTrue(booking(status = BookingStatus.CONFIRMED).canMarkPaid())
        assertFalse(booking(status = BookingStatus.CANCELLED).canMarkPaid())
        assertFalse(booking(status = BookingStatus.REJECTED).canMarkPaid())
        assertFalse(booking(payment = PaymentStatus.PAID).canMarkPaid())
    }

    @Test
    fun nightsAreAtLeastOne() {
        assertEquals(2, booking().nights())
        assertEquals(1, booking(checkIn = "2026-10-25", checkOut = "2026-10-25").nights())
        assertEquals(1, booking(checkIn = null).nights())
        assertEquals(3, booking(checkIn = "2026-10-25T00:00:00", checkOut = "2026-10-28").nights())
    }

    @Test
    fun bookingCodeIsZeroPadded() {
        assertEquals("BK000042", bookingCode(42))
        assertEquals("BK1234567", bookingCode(1_234_567))
    }

    @Test
    fun monthlyRevenueCountsPaidBookingsOnlyAndKeepsTheLastMonths() {
        val bookings = listOf(
            booking(payment = PaymentStatus.PAID, createdAt = "2026-08-03T10:00:00", total = 100.0),
            booking(payment = PaymentStatus.PAID, createdAt = "2026-09-10T10:00:00", total = 200.0),
            booking(payment = PaymentStatus.PAID, createdAt = "2026-09-20T10:00:00", total = 50.0),
            booking(payment = PaymentStatus.UNPAID, createdAt = "2026-10-01T10:00:00", total = 999.0),
            booking(payment = PaymentStatus.PAID, createdAt = "2026-10-02T10:00:00", total = 300.0),
        )

        assertEquals(listOf("2026-09" to 250.0, "2026-10" to 300.0), monthlyRevenue(bookings, months = 2))
    }

    @Test
    fun stayDatesMustStartTodayOrLaterAndEndAfterCheckIn() {
        assertTrue(isValidStay(today, today.plusDays(1), today))
        assertFalse(isValidStay(today.minusDays(1), today.plusDays(1), today))
        assertFalse(isValidStay(today.plusDays(2), today.plusDays(2), today))
        assertFalse(isValidStay(null, today, today))
    }
}
