package com.Nhom2.booking.event;

/** Published when a booking becomes PAID, whether confirmed by the Sepay webhook or by an admin. */
public record BookingPaidEvent(Long bookingId) {
}
