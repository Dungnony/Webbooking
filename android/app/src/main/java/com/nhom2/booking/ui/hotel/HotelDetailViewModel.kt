package com.nhom2.booking.ui.hotel

import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.CatalogApi
import com.nhom2.booking.data.catalog.HotelRepository
import com.nhom2.booking.data.model.CreateReviewBody
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.model.IdRef
import com.nhom2.booking.data.model.Review
import com.nhom2.booking.data.model.ReviewEligibility
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.data.session.SessionStore
import com.nhom2.booking.data.wishlist.WishlistRepository
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val REVIEW_PAGE_SIZE = 10
const val MIN_REVIEW_RATING = 1
const val MAX_REVIEW_RATING = 10

data class ReviewsState(
    val items: List<Review> = emptyList(),
    val nextPage: Int = 0,
    val totalPages: Int = 0,
    val total: Long = 0,
    val average: Double? = null,
    val loadingMore: Boolean = false,
) {
    val hasMore: Boolean get() = nextPage < totalPages
}

class HotelDetailViewModel(
    private val hotelId: Long,
    private val hotelRepository: HotelRepository,
    private val catalogApi: CatalogApi,
    private val wishlistRepository: WishlistRepository,
    private val sessionStore: SessionStore,
) : EventViewModel() {

    private val _hotel = MutableStateFlow<LoadState<Hotel>>(LoadState.Loading)
    val hotel: StateFlow<LoadState<Hotel>> = _hotel.asStateFlow()

    private val _reviews = MutableStateFlow(ReviewsState())
    val reviews: StateFlow<ReviewsState> = _reviews.asStateFlow()

    private val _eligibility = MutableStateFlow<ReviewEligibility?>(null)
    val eligibility: StateFlow<ReviewEligibility?> = _eligibility.asStateFlow()

    private val _submittingReview = MutableStateFlow(false)
    val submittingReview: StateFlow<Boolean> = _submittingReview.asStateFlow()

    val isSaved: StateFlow<Boolean> = wishlistRepository.ids
        .map { hotelId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _hotel.value = LoadState.Loading
            _hotel.value = try {
                LoadState.Success(hotelRepository.getHotel(hotelId))
            } catch (e: ApiException) {
                LoadState.Error(e.toUiText(UiText.Res(R.string.hotel_load_failed)))
            }
        }
        reloadReviews()
    }

    private fun reloadReviews() {
        _reviews.value = ReviewsState()
        loadMoreReviews()
        viewModelScope.launch {
            // Anonymous visitors get canReview=false with a "log in" reason; only signed-in users need the call.
            if (sessionStore.currentToken() == null) {
                _eligibility.value = null
                return@launch
            }
            _eligibility.value = try {
                apiCall { catalogApi.getReviewEligibility(hotelId) }
            } catch (e: ApiException) {
                null
            }
        }
    }

    fun loadMoreReviews() {
        val current = _reviews.value
        if (current.loadingMore) return
        _reviews.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = apiCall { catalogApi.getReviews(hotelId, current.nextPage, REVIEW_PAGE_SIZE) }
                _reviews.update {
                    it.copy(
                        items = it.items + page.content.filter { review -> it.items.none { old -> old.id == review.id } },
                        nextPage = page.page + 1,
                        totalPages = page.totalPages,
                        total = page.totalElements,
                        average = page.averageRating,
                    )
                }
            } catch (e: ApiException) {
                showError(e, R.string.reviews_load_failed)
            } finally {
                _reviews.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun submitReview(rating: Int, comment: String) {
        if (_submittingReview.value) return
        if (comment.isBlank()) {
            showMessage(R.string.review_comment_required)
            return
        }
        _submittingReview.value = true
        viewModelScope.launch {
            try {
                apiCall {
                    catalogApi.createReview(
                        CreateReviewBody(rating.coerceIn(MIN_REVIEW_RATING, MAX_REVIEW_RATING), comment.trim(), IdRef(hotelId)),
                    )
                }
                showMessage(R.string.review_submitted)
                // The hotel's average rating changes with the new review.
                hotelRepository.invalidate()
                load()
                complete()
            } catch (e: ApiException) {
                showError(e, R.string.review_submit_failed)
            } finally {
                _submittingReview.value = false
            }
        }
    }

    fun toggleSaved() {
        viewModelScope.launch {
            try {
                wishlistRepository.toggle(hotelId)
            } catch (e: ApiException) {
                showError(e, R.string.wishlist_update_failed)
            }
        }
    }
}
