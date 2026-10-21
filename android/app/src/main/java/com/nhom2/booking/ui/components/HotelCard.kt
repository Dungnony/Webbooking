package com.nhom2.booking.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom2.booking.BuildConfig
import com.nhom2.booking.R
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.domain.coverImage
import com.nhom2.booking.domain.lowestRoomPrice
import com.nhom2.booking.domain.ratingLabel
import com.nhom2.booking.ui.format.formatVnd
import com.nhom2.booking.ui.format.labelRes

@Composable
fun HotelCard(
    hotel: Hotel,
    isSaved: Boolean,
    onToggleSaved: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.height(150.dp)) {
            Box(modifier = Modifier.width(120.dp)) {
                RemoteImage(
                    url = hotel.coverImage(BuildConfig.API_BASE_URL),
                    contentDescription = hotel.name,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
                if (onToggleSaved != null) {
                    SaveButton(isSaved, onToggleSaved, Modifier.align(Alignment.TopEnd))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        hotel.name ?: stringResource(R.string.hotel_fallback_name),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 2.dp).width(14.dp),
                        )
                        Text(
                            hotel.city ?: hotel.address.orEmpty(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RatingBadge(hotel.ratingAvg)
                        Text(
                            stringResource(ratingLabel(hotel.ratingAvg).labelRes()),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                val price = hotel.lowestRoomPrice()
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    if (price != null) {
                        Text(stringResource(R.string.hotel_price_from), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatVnd(price), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    } else {
                        Text(stringResource(R.string.hotel_contact_for_price), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SaveButton(isSaved: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.9f), modifier = modifier.padding(6.dp)) {
        IconButton(onClick = onToggle) {
            Icon(
                if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(if (isSaved) R.string.wishlist_remove else R.string.wishlist_add),
                tint = if (isSaved) Color(0xFFE21C1C) else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Tiny clickable text link. */
@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clickable(onClick = onClick).padding(4.dp),
    )
}
