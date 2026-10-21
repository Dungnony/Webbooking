package com.nhom2.booking.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom2.booking.R
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.BrandTopBar
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.GuestsRoomsField
import com.nhom2.booking.ui.components.HotelCard
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.StayDatesField
import com.nhom2.booking.ui.theme.BookingDarkBlue
import com.nhom2.booking.ui.theme.BookingYellow
import java.time.LocalDate

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMessage: (UiText) -> Unit,
    onSearch: () -> Unit,
    onOpenHotel: (Long) -> Unit,
    onOpenAssistant: () -> Unit,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(
        topBar = {
            BrandTopBar(
                actions = {
                    IconButton(onClick = onOpenAssistant) {
                        Icon(Icons.Filled.SmartToy, contentDescription = stringResource(R.string.assistant_title))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BookingDarkBlue)
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.home_hero_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.home_hero_subtitle), color = Color.White.copy(alpha = 0.85f))
                }
            }
            item {
                SearchCard(
                    destination = query.destination,
                    onDestinationChange = viewModel::onDestinationChange,
                    checkIn = query.checkIn,
                    checkOut = query.checkOut,
                    onDatesChange = viewModel::onDatesChange,
                    guests = query.guests,
                    rooms = query.rooms,
                    onGuestsRoomsChange = viewModel::onGuestsRoomsChange,
                    onSearch = onSearch,
                )
            }
            when (val state = content) {
                LoadState.Loading -> item { LoadingBox(Modifier.height(200.dp)) }
                is LoadState.Error -> item {
                    ErrorBox(state.message, onRetry = { viewModel.load(forceRefresh = true) }, modifier = Modifier.height(260.dp))
                }
                is LoadState.Success -> {
                    if (state.data.popularCities.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.home_popular_destinations)) }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                items(state.data.popularCities) { city ->
                                    AssistChip(
                                        onClick = {
                                            viewModel.onDestinationChange(city)
                                            onSearch()
                                        },
                                        label = { Text(city) },
                                        leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null) },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.home_featured),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                viewModel.onDestinationChange("")
                                onSearch()
                            }) { Text(stringResource(R.string.view_all)) }
                        }
                    }
                    items(state.data.featured, key = { it.id }) { hotel ->
                        HotelCard(
                            hotel = hotel,
                            isSaved = hotel.id in savedIds,
                            onToggleSaved = { viewModel.toggleSaved(hotel.id) },
                            onClick = { onOpenHotel(hotel.id) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    item { WhyBookCard() }
                }
            }
        }
    }
}

@Composable
private fun SearchCard(
    destination: String,
    onDestinationChange: (String) -> Unit,
    checkIn: LocalDate,
    checkOut: LocalDate,
    onDatesChange: (LocalDate, LocalDate) -> Unit,
    guests: Int,
    rooms: Int,
    onGuestsRoomsChange: (Int, Int) -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(3.dp, BookingYellow),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = destination,
                onValueChange = onDestinationChange,
                label = { Text(stringResource(R.string.search_destination)) },
                placeholder = { Text(stringResource(R.string.search_destination_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            StayDatesField(checkIn, checkOut, onDatesChange)
            GuestsRoomsField(guests, rooms, onGuestsRoomsChange)
            Button(
                onClick = onSearch,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { Text(stringResource(R.string.search_button), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun WhyBookCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.home_why_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("✓ " + stringResource(R.string.home_why_price))
        Text("✓ " + stringResource(R.string.home_why_qr))
        Text("✓ " + stringResource(R.string.home_why_support))
    }
}
