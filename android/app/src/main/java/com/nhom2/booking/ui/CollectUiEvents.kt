package com.nhom2.booking.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

@Composable
fun CollectUiEvents(events: Flow<UiEvent>, onMessage: (UiText) -> Unit, onCompleted: () -> Unit) {
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> currentOnMessage(event.text)
                UiEvent.Completed -> currentOnCompleted()
            }
        }
    }
}
