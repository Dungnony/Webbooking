package com.nhom2.booking.data.catalog

import com.nhom2.booking.data.api.CatalogApi
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.network.apiCall
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Same lifetime the web app gives its hotel list cache.
private const val CACHE_TTL_MS = 5 * 60 * 1000L

/**
 * Hotel list shared by home, search and wishlist, kept in memory for a few minutes so switching tabs does not
 * reload it. Admin edits call [invalidate]. All calls throw ApiException on failure.
 */
class HotelRepository(
    private val api: CatalogApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var cached: List<Hotel>? = null
    private var cachedAt = 0L

    suspend fun getHotels(forceRefresh: Boolean = false): List<Hotel> = mutex.withLock {
        val current = cached
        if (!forceRefresh && current != null && clock() - cachedAt < CACHE_TTL_MS) return current
        apiCall { api.getHotels() }.also {
            cached = it
            cachedAt = clock()
        }
    }

    suspend fun getHotel(id: Long): Hotel = apiCall { api.getHotel(id) }

    fun invalidate() {
        cached = null
    }
}
