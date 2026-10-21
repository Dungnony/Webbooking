package com.nhom2.booking.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nhom2.booking.R
import com.nhom2.booking.data.network.ApiException

/** Text produced by a ViewModel: either a string resource or text the backend already translated. */
sealed interface UiText {
    data class Raw(val value: String) : UiText

    class Res(@param:StringRes val id: Int, vararg val args: Any) : UiText

    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res -> context.getString(id, *args)
    }
}

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)

/** One-shot things a screen reacts to once, as opposed to state it renders. */
sealed interface UiEvent {
    data class ShowMessage(val text: UiText) : UiEvent
    data object Completed : UiEvent
}

/** The backend's translated message when it sent one, otherwise [fallback]. */
fun ApiException.toUiText(fallback: UiText): UiText = when (this) {
    is ApiException.Network -> UiText.Res(R.string.error_server_unreachable)
    is ApiException.Http -> serverMessage?.let(UiText::Raw) ?: fallback
    is ApiException.UnexpectedResponse -> fallback
}
