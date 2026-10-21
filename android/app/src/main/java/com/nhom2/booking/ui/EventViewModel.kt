package com.nhom2.booking.ui

import androidx.lifecycle.ViewModel
import com.nhom2.booking.R
import com.nhom2.booking.data.network.ApiException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Data a screen loads once and can reload. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Error(val message: UiText) : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
}

/** ViewModel that can send one-shot [UiEvent]s (snackbar messages, "done") to its screen. */
abstract class EventViewModel : ViewModel() {
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    protected fun showMessage(text: UiText) {
        _events.trySend(UiEvent.ShowMessage(text))
    }

    protected fun showMessage(resId: Int, vararg args: Any) = showMessage(UiText.Res(resId, *args))

    protected fun showError(e: ApiException, fallbackResId: Int = R.string.error_generic_failed) =
        showMessage(e.toUiText(UiText.Res(fallbackResId)))

    protected fun complete() {
        _events.trySend(UiEvent.Completed)
    }
}
