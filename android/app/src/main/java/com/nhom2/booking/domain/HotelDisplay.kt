package com.nhom2.booking.domain

import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.model.Room

// Shared display rules so every screen shows the same price, photo and rating label (ported from the web app).

enum class RatingLabel { EXCEPTIONAL, VERY_GOOD, GOOD, OKAY, AVERAGE, NONE }

private fun Room.hasPrice() = (pricePerNight ?: 0.0) > 0.0

/** "From" price of a hotel: its cheapest priced room. The API returns rooms in no particular order. */
fun Hotel.lowestRoomPrice(): Double? = rooms.filter { it.hasPrice() }.minOfOrNull { it.pricePerNight!! }

/** Cheapest first, rooms without a price last. */
fun sortRoomsByPrice(rooms: List<Room>): List<Room> =
    rooms.sortedWith(compareBy<Room> { !it.hasPrice() }.thenBy { it.pricePerNight ?: 0.0 })

/** Word shown next to a 1-10 review score. */
fun ratingLabel(score: Double?): RatingLabel = when {
    score == null || score <= 0 -> RatingLabel.NONE
    score >= 9 -> RatingLabel.EXCEPTIONAL
    score >= 8 -> RatingLabel.VERY_GOOD
    score >= 7 -> RatingLabel.GOOD
    score >= 5 -> RatingLabel.OKAY
    else -> RatingLabel.AVERAGE
}

/**
 * Makes an image URL loadable: backend-relative paths ("/uploads/...") get the API host, and Google Drive share
 * links are turned into direct thumbnails.
 */
fun resolveImageUrl(url: String?, baseUrl: String): String? {
    val trimmed = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (trimmed.contains("drive.google.com")) {
        val id = Regex("id=([^&]+)").find(trimmed)?.groupValues?.get(1)
        if (id != null) return "https://drive.google.com/thumbnail?id=$id&sz=w1000"
    }
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return baseUrl.trimEnd('/') + "/" + trimmed.trimStart('/')
}

/** Hotel photo, else the first room photo. */
fun Hotel.coverImage(baseUrl: String): String? =
    resolveImageUrl(imageUrl?.takeIf { it.isNotBlank() } ?: rooms.firstNotNullOfOrNull { room ->
        room.images.firstNotNullOfOrNull { it.imageUrl?.takeIf(String::isNotBlank) }
    }, baseUrl)

/** Hotel photo followed by every room photo, without duplicates. */
fun Hotel.allImages(baseUrl: String): List<String> =
    (listOf(imageUrl) + rooms.flatMap { room -> room.images.map { it.imageUrl } })
        .mapNotNull { resolveImageUrl(it, baseUrl) }
        .distinct()
