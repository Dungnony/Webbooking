package com.nhom2.booking.ui.search

import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.catalog.HotelRepository
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.wishlist.WishlistRepository
import com.nhom2.booking.domain.Amenity
import com.nhom2.booking.domain.PropertyType
import com.nhom2.booking.domain.SearchCriteria
import com.nhom2.booking.domain.SortOrder
import com.nhom2.booking.domain.StayQuery
import com.nhom2.booking.domain.filterHotels
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Filters picked in the filter sheet; the destination, guests and rooms come from the shared [StayQuery]. */
data class SearchFilters(
    val minRating: Double? = null,
    val maxPrice: Double? = null,
    val propertyTypes: Set<PropertyType> = emptySet(),
    val amenities: Set<Amenity> = emptySet(),
    val sort: SortOrder = SortOrder.RATING,
)

class SearchViewModel(
    private val hotelRepository: HotelRepository,
    private val wishlistRepository: WishlistRepository,
    private val stayQuery: MutableStateFlow<StayQuery>,
) : EventViewModel() {

    private val allHotels = MutableStateFlow<LoadState<List<Hotel>>>(LoadState.Loading)
    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    val query: StateFlow<StayQuery> = stayQuery.asStateFlow()
    val savedIds: StateFlow<List<Long>> = wishlistRepository.ids

    val results: StateFlow<LoadState<List<Hotel>>> =
        combine(allHotels, stayQuery, _filters) { hotels, query, filters ->
            when (hotels) {
                is LoadState.Success -> LoadState.Success(filterHotels(hotels.data, criteria(query, filters)))
                else -> hotels
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (allHotels.value !is LoadState.Success) allHotels.value = LoadState.Loading
            allHotels.value = try {
                LoadState.Success(hotelRepository.getHotels(forceRefresh))
            } catch (e: ApiException) {
                LoadState.Error(e.toUiText(UiText.Res(R.string.hotels_load_failed)))
            }
        }
    }

    fun onDestinationChange(value: String) = stayQuery.update { it.copy(destination = value) }

    fun onDatesChange(checkIn: LocalDate, checkOut: LocalDate) =
        stayQuery.update { it.copy(checkIn = checkIn, checkOut = checkOut) }

    fun onGuestsRoomsChange(guests: Int, rooms: Int) = stayQuery.update { it.copy(guests = guests, rooms = rooms) }

    fun applyFilters(filters: SearchFilters) {
        _filters.value = filters
    }

    fun clearFilters() {
        _filters.value = SearchFilters(sort = _filters.value.sort)
    }

    fun toggleSaved(hotelId: Long) {
        viewModelScope.launch {
            try {
                wishlistRepository.toggle(hotelId)
            } catch (e: ApiException) {
                showError(e, R.string.wishlist_update_failed)
            }
        }
    }

    private fun criteria(query: StayQuery, filters: SearchFilters) = SearchCriteria(
        destination = query.destination,
        guests = query.guests,
        rooms = query.rooms,
        minRating = filters.minRating,
        maxPrice = filters.maxPrice,
        propertyTypes = filters.propertyTypes,
        amenities = filters.amenities,
        sort = filters.sort,
    )
}
