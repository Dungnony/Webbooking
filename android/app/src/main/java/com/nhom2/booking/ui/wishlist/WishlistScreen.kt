package com.nhom2.booking.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom2.booking.R
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ConfirmDialog
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.HotelCard
import com.nhom2.booking.ui.components.LoadingBox

@Composable
fun WishlistScreen(
    viewModel: WishlistViewModel,
    onMessage: (UiText) -> Unit,
    onOpenHotel: (Long) -> Unit,
    onExplore: () -> Unit,
) {
    val state by viewModel.savedHotels.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    val hotels = (state as? LoadState.Success)?.data.orEmpty()
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.tab_wishlist),
                actions = {
                    if (hotels.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(stringResource(R.string.wishlist_clear_all), color = Color.White)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is LoadState.Success -> if (current.data.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.FavoriteBorder,
                    title = stringResource(R.string.wishlist_empty_title),
                    body = stringResource(R.string.wishlist_empty_body),
                    actionLabel = stringResource(R.string.wishlist_explore),
                    onAction = onExplore,
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            pluralStringResource(R.plurals.wishlist_count, current.data.size, current.data.size),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(current.data, key = { it.id }) { hotel ->
                        HotelCard(
                            hotel = hotel,
                            isSaved = true,
                            onToggleSaved = { viewModel.remove(hotel.id) },
                            onClick = { onOpenHotel(hotel.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.wishlist_clear_all),
            text = stringResource(R.string.wishlist_clear_confirm),
            confirmLabel = stringResource(R.string.wishlist_clear_all),
            onConfirm = {
                viewModel.clearAll()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}
