package com.nhom2.booking.domain

import com.nhom2.booking.data.model.Hotel

enum class SortOrder { RATING, PRICE_LOW, PRICE_HIGH, NAME }

// Keywords are matched against the hotels' Vietnamese descriptions (as on the web), so only labels are translated.
enum class PropertyType(val keyword: String) {
    HOTEL("khách sạn"),
    RESORT("resort"),
    APARTMENT("căn hộ"),
    VILLA("biệt thự"),
    HOMESTAY("nhà dân"),
    HOLIDAY_HOME("nhà nghỉ mát"),
}

enum class Amenity(val keyword: String) {
    BREAKFAST("bữa sáng"),
    POOL("hồ bơi"),
    WIFI("wifi"),
    BEACHFRONT("biển"),
    AIRPORT_SHUTTLE("sân bay"),
    PARKING("đậu xe"),
    SPA("spa"),
    GYM("gym"),
    FREE_CANCELLATION("hủy"),
}

data class SearchCriteria(
    val destination: String = "",
    val guests: Int = 2,
    val rooms: Int = 1,
    val minRating: Double? = null,
    val maxPrice: Double? = null,
    val propertyTypes: Set<PropertyType> = emptySet(),
    val amenities: Set<Amenity> = emptySet(),
    val sort: SortOrder = SortOrder.RATING,
)

/**
 * Same rules as the web search page: destination matches city, name or address; property types are OR-ed;
 * amenities are AND-ed; the hotel's rooms must fit the requested guests and rooms.
 */
fun filterHotels(hotels: List<Hotel>, criteria: SearchCriteria): List<Hotel> {
    val destination = criteria.destination.trim().lowercase()
    return hotels
        .asSequence()
        .filter { hotel ->
            destination.isEmpty() || listOfNotNull(hotel.city, hotel.name, hotel.address)
                .any { it.lowercase().contains(destination) }
        }
        .filter { hotel -> criteria.minRating == null || (hotel.ratingAvg ?: 0.0) >= criteria.minRating }
        .filter { hotel -> criteria.maxPrice == null || (hotel.lowestRoomPrice() ?: 0.0) <= criteria.maxPrice }
        .filter { hotel ->
            criteria.propertyTypes.isEmpty() || criteria.propertyTypes.any { hotel.searchText().contains(it.keyword) }
        }
        .filter { hotel -> criteria.amenities.all { hotel.searchText().contains(it.keyword) } }
        .filter { hotel -> hotel.fits(criteria.guests, criteria.rooms) }
        .toList()
        .let { sortHotels(it, criteria.sort) }
}

private fun Hotel.searchText() = "${name.orEmpty()} ${description.orEmpty()}".lowercase()

private fun Hotel.fits(guests: Int, roomCount: Int): Boolean {
    if (rooms.isEmpty()) return false
    val capacity = rooms.sumOf { (it.capacity ?: 0) * (it.quantity ?: 0) }
    val available = rooms.sumOf { it.quantity ?: 0 }
    return capacity >= guests && available >= roomCount
}

fun sortHotels(hotels: List<Hotel>, sort: SortOrder): List<Hotel> = when (sort) {
    SortOrder.RATING -> hotels.sortedByDescending { it.ratingAvg ?: 0.0 }
    SortOrder.PRICE_LOW -> hotels.sortedBy { it.lowestRoomPrice() ?: Double.MAX_VALUE }
    SortOrder.PRICE_HIGH -> hotels.sortedByDescending { it.lowestRoomPrice() ?: 0.0 }
    SortOrder.NAME -> hotels.sortedBy { it.name.orEmpty().lowercase() }
}
