package com.nhom2.booking.domain

import java.time.LocalDate

const val MIN_GUESTS = 1
const val MAX_GUESTS = 30
const val MIN_ROOMS = 1
const val MAX_ROOMS = 30

/** What the guest searched for; it pre-fills the search results and the booking form. */
data class StayQuery(
    val destination: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guests: Int,
    val rooms: Int,
) {
    val nights: Int get() = nightsBetween(checkIn, checkOut)

    companion object {
        // Same defaults as the web booking form: tomorrow, for two nights.
        fun default(today: LocalDate) = StayQuery(
            destination = "",
            checkIn = today.plusDays(1),
            checkOut = today.plusDays(3),
            guests = 2,
            rooms = 1,
        )
    }
}

/** Dates the backend will accept: check-in today or later, check-out after check-in. */
fun isValidStay(checkIn: LocalDate?, checkOut: LocalDate?, today: LocalDate): Boolean =
    checkIn != null && checkOut != null && !checkIn.isBefore(today) && checkOut.isAfter(checkIn)
