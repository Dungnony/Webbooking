package com.nhom2.booking.ui.admin

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A list filtered by status on the server, whose rows have actions that change one item and reload the list
 * (bookings and booking requests).
 */
abstract class AdminListViewModel<T> : EventViewModel() {

    private val _items = MutableStateFlow<LoadState<List<T>>>(LoadState.Loading)
    val items: StateFlow<LoadState<List<T>>> = _items.asStateFlow()

    /** Null means every status. */
    private val _filter = MutableStateFlow<String?>(null)
    val filter: StateFlow<String?> = _filter.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _busyId = MutableStateFlow<Long?>(null)
    val busyId: StateFlow<Long?> = _busyId.asStateFlow()

    protected abstract suspend fun fetch(filter: String?): List<T>

    fun load() {
        viewModelScope.launch {
            if (_items.value is LoadState.Success) _refreshing.value = true else _items.value = LoadState.Loading
            try {
                _items.value = LoadState.Success(fetch(_filter.value))
            } catch (e: ApiException) {
                if (_items.value is LoadState.Success) {
                    showError(e, R.string.admin_list_load_failed)
                } else {
                    _items.value = LoadState.Error(e.toUiText(UiText.Res(R.string.admin_list_load_failed)))
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun setFilter(status: String?) {
        if (_filter.value == status) return
        _filter.value = status
        _items.value = LoadState.Loading
        load()
    }

    protected fun runAction(id: Long, @StringRes successRes: Int, action: suspend () -> Unit) {
        if (_busyId.value != null) return
        _busyId.value = id
        viewModelScope.launch {
            try {
                apiCall { action() }
                showMessage(successRes)
                load()
            } catch (e: ApiException) {
                showError(e)
            } finally {
                _busyId.value = null
            }
        }
    }
}
