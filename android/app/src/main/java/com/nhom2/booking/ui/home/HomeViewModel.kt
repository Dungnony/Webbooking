package com.nhom2.booking.ui.home

import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.catalog.HotelRepository
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.wishlist.WishlistRepository
import com.nhom2.booking.domain.StayQuery
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val FEATURED_COUNT = 8
private const val POPULAR_CITY_COUNT = 8

data class HomeContent(val featured: List<Hotel>, val popularCities: List<String>)

class HomeViewModel(
    private val hotelRepository: HotelRepository,
    private val wishlistRepository: WishlistRepository,
    private val stayQuery: MutableStateFlow<StayQuery>,
) : EventViewModel() {

    private val _content = MutableStateFlow<LoadState<HomeContent>>(LoadState.Loading)
    val content: StateFlow<LoadState<HomeContent>> = _content.asStateFlow()

    val query: StateFlow<StayQuery> = stayQuery.asStateFlow()
    val savedIds: StateFlow<List<Long>> = wishlistRepository.ids

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_content.value !is LoadState.Success) _content.value = LoadState.Loading
            _content.value = try {
                val hotels = hotelRepository.getHotels(forceRefresh)
                LoadState.Success(
                    HomeContent(
                        featured = hotels.sortedByDescending { it.ratingAvg ?: 0.0 }.take(FEATURED_COUNT),
                        popularCities = hotels
                            .mapNotNull { it.city?.trim()?.takeIf(String::isNotEmpty) }
                            .groupingBy { it }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }
                            .take(POPULAR_CITY_COUNT)
                            .map { it.key },
                    ),
                )
            } catch (e: ApiException) {
                LoadState.Error(e.toUiText(UiText.Res(R.string.hotels_load_failed)))
            }
        }
    }

    fun onDestinationChange(value: String) = stayQuery.update { it.copy(destination = value) }

    fun onDatesChange(checkIn: LocalDate, checkOut: LocalDate) =
        stayQuery.update { it.copy(checkIn = checkIn, checkOut = checkOut) }

    fun onGuestsRoomsChange(guests: Int, rooms: Int) = stayQuery.update { it.copy(guests = guests, rooms = rooms) }

    fun toggleSaved(hotelId: Long) {
        viewModelScope.launch {
            try {
                wishlistRepository.toggle(hotelId)
            } catch (e: ApiException) {
                showError(e, R.string.wishlist_update_failed)
            }
        }
    }
}
