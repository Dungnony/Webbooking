package com.nhom2.booking.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom2.booking.R
import com.nhom2.booking.domain.Amenity
import com.nhom2.booking.domain.PropertyType
import com.nhom2.booking.domain.SortOrder
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.GuestsRoomsField
import com.nhom2.booking.ui.components.HotelCard
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.StayDatesField
import com.nhom2.booking.ui.format.formatVnd

private val RATING_OPTIONS = listOf(9.0, 8.0, 7.0, 6.0)
private val PRICE_OPTIONS = listOf(500_000.0, 1_000_000.0, 2_000_000.0, 5_000_000.0)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onMessage: (UiText) -> Unit,
    onOpenHotel: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.search_results_title),
                onBack = onBack,
                actions = {
                    BadgedBox(badge = {
                        val count = activeFilterCount(filters)
                        if (count > 0) Badge { Text(count.toString()) }
                    }) {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.filters_title))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query.destination,
                        onValueChange = viewModel::onDestinationChange,
                        label = { Text(stringResource(R.string.search_destination)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StayDatesField(query.checkIn, query.checkOut, viewModel::onDatesChange)
                    GuestsRoomsField(query.guests, query.rooms, viewModel::onGuestsRoomsChange)
                }
            }
            when (val state = results) {
                LoadState.Loading -> item { LoadingBox(Modifier.padding(top = 48.dp)) }
                is LoadState.Error -> item { ErrorBox(state.message, onRetry = { viewModel.load(forceRefresh = true) }) }
                is LoadState.Success -> {
                    item {
                        Text(
                            pluralStringResource(R.plurals.search_result_count, state.data.size, state.data.size),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                    if (state.data.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Filled.SearchOff,
                                title = stringResource(R.string.search_empty_title),
                                body = stringResource(R.string.search_empty_body),
                                actionLabel = stringResource(R.string.filters_clear),
                                onAction = viewModel::clearFilters,
                            )
                        }
                    }
                    items(state.data, key = { it.id }) { hotel ->
                        HotelCard(
                            hotel = hotel,
                            isSaved = hotel.id in savedIds,
                            onToggleSaved = { viewModel.toggleSaved(hotel.id) },
                            onClick = { onOpenHotel(hotel.id) },
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            initial = filters,
            onApply = {
                viewModel.applyFilters(it)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

private fun activeFilterCount(filters: SearchFilters): Int =
    listOfNotNull(filters.minRating, filters.maxPrice).size + filters.propertyTypes.size + filters.amenities.size

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(initial: SearchFilters, onApply: (SearchFilters) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filters_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = SearchFilters(sort = draft.sort) }) { Text(stringResource(R.string.filters_clear)) }
            }

            FilterGroup(stringResource(R.string.filters_sort)) {
                SortOrder.entries.forEach { sort ->
                    FilterChip(
                        selected = draft.sort == sort,
                        onClick = { draft = draft.copy(sort = sort) },
                        label = { Text(stringResource(sort.labelRes())) },
                    )
                }
            }
            FilterGroup(stringResource(R.string.filters_rating)) {
                RATING_OPTIONS.forEach { rating ->
                    FilterChip(
                        selected = draft.minRating == rating,
                        onClick = { draft = draft.copy(minRating = if (draft.minRating == rating) null else rating) },
                        label = { Text(stringResource(R.string.filters_rating_at_least, rating.toInt())) },
                    )
                }
            }
            FilterGroup(stringResource(R.string.filters_max_price)) {
                PRICE_OPTIONS.forEach { price ->
                    FilterChip(
                        selected = draft.maxPrice == price,
                        onClick = { draft = draft.copy(maxPrice = if (draft.maxPrice == price) null else price) },
                        label = { Text(stringResource(R.string.filters_price_up_to, formatVnd(price))) },
                    )
                }
            }
            FilterGroup(stringResource(R.string.filters_property_type)) {
                PropertyType.entries.forEach { type ->
                    FilterChip(
                        selected = type in draft.propertyTypes,
                        onClick = { draft = draft.copy(propertyTypes = draft.propertyTypes.toggle(type)) },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
            FilterGroup(stringResource(R.string.filters_amenities)) {
                Amenity.entries.forEach { amenity ->
                    FilterChip(
                        selected = amenity in draft.amenities,
                        onClick = { draft = draft.copy(amenities = draft.amenities.toggle(amenity)) },
                        label = { Text(stringResource(amenity.labelRes())) },
                    )
                }
            }

            Button(
                onClick = { onApply(draft) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) { Text(stringResource(R.string.filters_apply), fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

@StringRes
private fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.RATING -> R.string.sort_rating
    SortOrder.PRICE_LOW -> R.string.sort_price_low
    SortOrder.PRICE_HIGH -> R.string.sort_price_high
    SortOrder.NAME -> R.string.sort_name
}

@StringRes
private fun PropertyType.labelRes(): Int = when (this) {
    PropertyType.HOTEL -> R.string.property_hotel
    PropertyType.RESORT -> R.string.property_resort
    PropertyType.APARTMENT -> R.string.property_apartment
    PropertyType.VILLA -> R.string.property_villa
    PropertyType.HOMESTAY -> R.string.property_homestay
    PropertyType.HOLIDAY_HOME -> R.string.property_holiday_home
}

@StringRes
private fun Amenity.labelRes(): Int = when (this) {
    Amenity.BREAKFAST -> R.string.amenity_breakfast
    Amenity.POOL -> R.string.amenity_pool
    Amenity.WIFI -> R.string.amenity_wifi
    Amenity.BEACHFRONT -> R.string.amenity_beachfront
    Amenity.AIRPORT_SHUTTLE -> R.string.amenity_airport_shuttle
    Amenity.PARKING -> R.string.amenity_parking
    Amenity.SPA -> R.string.amenity_spa
    Amenity.GYM -> R.string.amenity_gym
    Amenity.FREE_CANCELLATION -> R.string.amenity_free_cancellation
}
