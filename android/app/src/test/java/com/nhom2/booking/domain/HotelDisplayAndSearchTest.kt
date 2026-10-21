package com.nhom2.booking.domain

import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.model.Room
import com.nhom2.booking.data.model.RoomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotelDisplayAndSearchTest {

    private val base = "https://api.example.com/"

    private fun room(id: Long, price: Double?, capacity: Int = 2, quantity: Int = 1, image: String? = null) =
        Room(id = id, pricePerNight = price, capacity = capacity, quantity = quantity, images = listOfNotNull(image).map { RoomImage(imageUrl = it) })

    private fun hotel(
        id: Long,
        name: String = "Hotel $id",
        city: String? = null,
        description: String? = null,
        rating: Double? = null,
        rooms: List<Room> = listOf(room(id * 10, 1_000_000.0)),
        imageUrl: String? = null,
    ) = Hotel(id = id, name = name, city = city, description = description, ratingAvg = rating, rooms = rooms, imageUrl = imageUrl)

    @Test
    fun lowestPriceIgnoresRoomsWithoutAPrice() {
        val h = hotel(1, rooms = listOf(room(1, 2_200_000.0), room(2, null), room(3, 0.0), room(4, 900_000.0)))

        assertEquals(900_000.0, h.lowestRoomPrice()!!, 0.0)
        assertNull(hotel(2, rooms = listOf(room(5, null))).lowestRoomPrice())
    }

    @Test
    fun roomsSortCheapestFirstAndUnpricedLast() {
        val sorted = sortRoomsByPrice(listOf(room(1, 3.0), room(2, null), room(3, 1.0)))

        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.id })
    }

    @Test
    fun ratingLabelsMatchTheWebThresholds() {
        assertEquals(RatingLabel.EXCEPTIONAL, ratingLabel(9.0))
        assertEquals(RatingLabel.VERY_GOOD, ratingLabel(8.9))
        assertEquals(RatingLabel.GOOD, ratingLabel(7.0))
        assertEquals(RatingLabel.OKAY, ratingLabel(5.0))
        assertEquals(RatingLabel.AVERAGE, ratingLabel(0.5))
        assertEquals(RatingLabel.NONE, ratingLabel(0.0))
        assertEquals(RatingLabel.NONE, ratingLabel(null))
    }

    @Test
    fun imageUrlsAreMadeLoadable() {
        assertEquals("https://api.example.com/uploads/hotels/a.jpg", resolveImageUrl("/uploads/hotels/a.jpg", base))
        assertEquals("https://cdn.example.com/a.jpg", resolveImageUrl(" https://cdn.example.com/a.jpg ", base))
        assertEquals(
            "https://drive.google.com/thumbnail?id=abc123&sz=w1000",
            resolveImageUrl("https://drive.google.com/open?id=abc123&usp=sharing", base),
        )
        assertNull(resolveImageUrl("  ", base))
        assertNull(resolveImageUrl(null, base))
    }

    @Test
    fun coverFallsBackToTheFirstRoomPhoto() {
        val h = hotel(1, rooms = listOf(room(1, 1.0), room(2, 1.0, image = "/uploads/rooms/r.png")))

        assertEquals("https://api.example.com/uploads/rooms/r.png", h.coverImage(base))
        assertEquals(listOf("https://api.example.com/uploads/rooms/r.png"), h.allImages(base))
    }

    @Test
    fun destinationMatchesCityNameOrAddressIgnoringCase() {
        val hotels = listOf(hotel(1, city = "Hà Nội"), hotel(2, name = "Sunrise Đà Nẵng"), hotel(3, city = "Huế"))

        assertEquals(listOf(1L), filterHotels(hotels, SearchCriteria(destination = "hà nội")).map { it.id })
        assertEquals(listOf(2L), filterHotels(hotels, SearchCriteria(destination = "ĐÀ NẴNG")).map { it.id })
        assertEquals(3, filterHotels(hotels, SearchCriteria()).size)
    }

    @Test
    fun propertyTypesAreOredAndAmenitiesAnded() {
        val hotels = listOf(
            hotel(1, description = "Khách sạn có hồ bơi và wifi"),
            hotel(2, description = "Resort có hồ bơi"),
            hotel(3, description = "Căn hộ có wifi"),
        )

        val anyType = SearchCriteria(propertyTypes = setOf(PropertyType.HOTEL, PropertyType.RESORT))
        assertEquals(setOf(1L, 2L), filterHotels(hotels, anyType).map { it.id }.toSet())

        val allAmenities = SearchCriteria(amenities = setOf(Amenity.POOL, Amenity.WIFI))
        assertEquals(listOf(1L), filterHotels(hotels, allAmenities).map { it.id })
    }

    @Test
    fun hotelsMustFitTheGuestsAndRooms() {
        val small = hotel(1, rooms = listOf(room(1, 1.0, capacity = 2, quantity = 1)))
        val big = hotel(2, rooms = listOf(room(2, 1.0, capacity = 2, quantity = 3)))
        val empty = hotel(3, rooms = emptyList())

        val result = filterHotels(listOf(small, big, empty), SearchCriteria(guests = 4, rooms = 2))

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun ratingAndPriceFiltersAndSorting() {
        val hotels = listOf(
            hotel(1, name = "B", rating = 9.2, rooms = listOf(room(1, 3_000_000.0))),
            hotel(2, name = "A", rating = 7.5, rooms = listOf(room(2, 800_000.0))),
            hotel(3, name = "C", rating = 8.1, rooms = listOf(room(3, 1_500_000.0))),
        )

        assertEquals(listOf(1L, 3L), filterHotels(hotels, SearchCriteria(minRating = 8.0)).map { it.id })
        assertEquals(listOf(3L, 2L), filterHotels(hotels, SearchCriteria(maxPrice = 2_000_000.0)).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), filterHotels(hotels, SearchCriteria(sort = SortOrder.PRICE_LOW)).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), filterHotels(hotels, SearchCriteria(sort = SortOrder.PRICE_HIGH)).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), filterHotels(hotels, SearchCriteria(sort = SortOrder.NAME)).map { it.id })
    }

    @Test
    fun phoneNumbersAcceptCommonVietnameseFormats() {
        assertEquals(true, isValidPhone("0912345678"))
        assertEquals(true, isValidPhone("+84 912 345 678"))
        assertEquals(true, isValidPhone("091.234.5678"))
        assertEquals(false, isValidPhone("12345"))
        assertEquals(false, isValidPhone("09123abc78"))
    }
}
