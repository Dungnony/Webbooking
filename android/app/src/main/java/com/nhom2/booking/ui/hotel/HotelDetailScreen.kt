package com.nhom2.booking.ui.hotel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom2.booking.BuildConfig
import com.nhom2.booking.R
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.model.Review
import com.nhom2.booking.data.model.ReviewEligibility
import com.nhom2.booking.data.model.Room
import com.nhom2.booking.domain.allImages
import com.nhom2.booking.domain.lowestRoomPrice
import com.nhom2.booking.domain.ratingLabel
import com.nhom2.booking.domain.resolveImageUrl
import com.nhom2.booking.domain.sortRoomsByPrice
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.RatingBadge
import com.nhom2.booking.ui.components.RemoteImage
import com.nhom2.booking.ui.format.formatDate
import com.nhom2.booking.ui.format.formatVnd
import com.nhom2.booking.ui.format.labelRes
import com.nhom2.booking.ui.theme.BookingYellow

@Composable
fun HotelDetailScreen(
    viewModel: HotelDetailViewModel,
    onMessage: (UiText) -> Unit,
    onBook: (roomId: Long?) -> Unit,
    onAskAssistant: () -> Unit,
    onBack: () -> Unit,
) {
    val hotelState by viewModel.hotel.collectAsStateWithLifecycle()
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val eligibility by viewModel.eligibility.collectAsStateWithLifecycle()
    val submitting by viewModel.submittingReview.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    var reviewFormKey by rememberSaveable { mutableIntStateOf(0) }
    // A completed event means the review was posted, so the form starts over.
    CollectUiEvents(viewModel.events, onMessage, onCompleted = { reviewFormKey++ })

    val hotel = (hotelState as? LoadState.Success)?.data
    Scaffold(
        topBar = {
            AppTopBar(
                title = hotel?.name ?: stringResource(R.string.hotel_fallback_name),
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::toggleSaved) {
                        Icon(
                            if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(if (isSaved) R.string.wishlist_remove else R.string.wishlist_add),
                        )
                    }
                },
            )
        },
        bottomBar = { if (hotel != null) BookBar(hotel, onBook = { onBook(null) }) },
    ) { padding ->
        when (val state = hotelState) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> ErrorBox(state.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is LoadState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { ImageGallery(state.data) }
                item { Overview(state.data, reviews.total, onAskAssistant) }
                item { SectionTitle(stringResource(R.string.hotel_rooms_title)) }
                val rooms = sortRoomsByPrice(state.data.rooms)
                if (rooms.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.hotel_no_rooms),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                items(rooms, key = { it.id }) { room -> RoomCard(room, onBook = { onBook(room.id) }) }
                item { Policies() }
                item {
                    ReviewsHeader(reviews.average, reviews.total)
                }
                eligibility?.let { current ->
                    item(key = "review-form-$reviewFormKey") {
                        ReviewForm(current, submitting, onSubmit = viewModel::submitReview)
                    }
                }
                items(reviews.items, key = { "review-${it.id}" }) { review -> ReviewItem(review) }
                if (reviews.hasMore) {
                    item {
                        OutlinedButton(
                            onClick = viewModel::loadMoreReviews,
                            enabled = !reviews.loadingMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) { Text(stringResource(R.string.reviews_load_more)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGallery(hotel: Hotel) {
    val images = hotel.allImages(BuildConfig.API_BASE_URL)
    if (images.isEmpty()) {
        RemoteImage(url = null, modifier = Modifier.fillMaxWidth().height(220.dp))
        return
    }
    val pagerState = rememberPagerState(pageCount = { images.size })
    Box {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(240.dp)) { page ->
            RemoteImage(url = images[page], contentDescription = hotel.name, modifier = Modifier.fillMaxSize())
        }
        if (images.size > 1) {
            Text(
                "${pagerState.currentPage + 1}/${images.size}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Overview(hotel: Hotel, reviewTotal: Long, onAskAssistant: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(hotel.name ?: stringResource(R.string.hotel_fallback_name), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(
                listOfNotNull(hotel.address, hotel.city).joinToString(", "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RatingBadge(hotel.ratingAvg)
            Text(stringResource(ratingLabel(hotel.ratingAvg).labelRes()), fontWeight = FontWeight.Bold)
            if (reviewTotal > 0) {
                Text(
                    pluralStringResource(R.plurals.review_count, reviewTotal.toInt(), reviewTotal.toInt()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            hotel.description?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.hotel_default_description, hotel.name.orEmpty(), hotel.city.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onAskAssistant, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.hotel_ask_assistant))
        }
    }
}

@Composable
private fun RoomCard(room: Room, onBook: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteImage(
                url = resolveImageUrl(room.images.firstOrNull()?.imageUrl, BuildConfig.API_BASE_URL),
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(room.name ?: room.type.orEmpty(), fontWeight = FontWeight.Bold)
                if (!room.type.isNullOrBlank() && room.type != room.name) {
                    Text(room.type, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                room.capacity?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(pluralStringResource(R.plurals.guests, it, it), fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(formatVnd(room.pricePerNight), fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.hotel_per_night), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onBook, shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.hotel_choose_room)) }
                }
            }
        }
    }
}

@Composable
private fun Policies() {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.hotel_policies_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.hotel_policy_check_in))
        Text(stringResource(R.string.hotel_policy_check_out))
        Text(stringResource(R.string.hotel_policy_cancel))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ReviewsHeader(average: Double?, total: Long) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.reviews_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        if (total == 0L) {
            Text(stringResource(R.string.reviews_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingBadge(average)
                Text(stringResource(ratingLabel(average).labelRes()), fontWeight = FontWeight.Bold)
                Text(
                    pluralStringResource(R.plurals.review_count, total.toInt(), total.toInt()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReviewForm(eligibility: ReviewEligibility, submitting: Boolean, onSubmit: (Int, String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.review_write_title), fontWeight = FontWeight.Bold)
            if (eligibility.canReview) {
                var rating by rememberSaveable { mutableIntStateOf(0) }
                var comment by rememberSaveable { mutableStateOf("") }
                RatingPicker(rating, onChange = { rating = it })
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.review_comment_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSubmit(rating, comment) },
                    enabled = !submitting && rating >= MIN_REVIEW_RATING && comment.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.review_submit)) }
            } else {
                // The backend explains why (no completed stay yet, already reviewed...), already translated.
                Text(eligibility.reason ?: stringResource(R.string.review_not_eligible), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RatingPicker(value: Int, onChange: (Int) -> Unit) {
    Column {
        Row {
            (MIN_REVIEW_RATING..MAX_REVIEW_RATING).forEach { score ->
                IconButton(onClick = { onChange(score) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (score <= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = score.toString(),
                        tint = BookingYellow,
                    )
                }
            }
        }
        Text(
            if (value == 0) stringResource(R.string.review_pick_rating) else "$value/$MAX_REVIEW_RATING",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewItem(review: Review) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RatingBadge(review.rating?.toDouble())
            Column(modifier = Modifier.weight(1f)) {
                Text(review.username ?: stringResource(R.string.review_anonymous), fontWeight = FontWeight.Bold)
                Text(formatDate(review.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!review.comment.isNullOrBlank()) Text(review.comment)
        if (!review.hotelReply.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.review_hotel_reply), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(review.hotelReply, fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun BookBar(hotel: Hotel, onBook: () -> Unit) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val price = hotel.lowestRoomPrice()
            Column(modifier = Modifier.weight(1f)) {
                if (price != null) {
                    Text(stringResource(R.string.hotel_price_from), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatVnd(price), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                } else {
                    Text(stringResource(R.string.hotel_contact_for_price), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onBook, enabled = hotel.rooms.isNotEmpty(), shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.hotel_book_now), fontWeight = FontWeight.Bold)
            }
        }
    }
}
