package com.nhom2.booking.ui.wishlist

import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.catalog.HotelRepository
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.wishlist.WishlistRepository
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WishlistViewModel(
    private val hotelRepository: HotelRepository,
    private val wishlistRepository: WishlistRepository,
) : EventViewModel() {

    private val allHotels = MutableStateFlow<LoadState<List<Hotel>>>(LoadState.Loading)

    /** Saved hotels, newest first; ids of hotels that no longer exist are skipped. */
    val savedHotels: StateFlow<LoadState<List<Hotel>>> =
        combine(allHotels, wishlistRepository.ids) { hotels, ids ->
            when (hotels) {
                is LoadState.Success -> {
                    val byId = hotels.data.associateBy { it.id }
                    LoadState.Success(ids.mapNotNull { byId[it] })
                }
                else -> hotels
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (allHotels.value !is LoadState.Success) allHotels.value = LoadState.Loading
            try {
                wishlistRepository.refresh()
                allHotels.value = LoadState.Success(hotelRepository.getHotels())
            } catch (e: ApiException) {
                allHotels.value = LoadState.Error(e.toUiText(UiText.Res(R.string.wishlist_load_failed)))
            }
        }
    }

    fun remove(hotelId: Long) {
        viewModelScope.launch {
            try {
                wishlistRepository.toggle(hotelId)
            } catch (e: ApiException) {
                showError(e, R.string.wishlist_update_failed)
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                wishlistRepository.clear()
            } catch (e: ApiException) {
                showError(e, R.string.wishlist_update_failed)
            }
        }
    }
}
