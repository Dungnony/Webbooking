package com.nhom2.booking.ui.admin

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AdminApi
import com.nhom2.booking.data.files.MIME_XLSX
import com.nhom2.booking.data.files.openDocument
import com.nhom2.booking.data.files.saveToCache
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.domain.monthlyRevenue
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.SectionCard
import com.nhom2.booking.ui.format.StatusError
import com.nhom2.booking.ui.format.StatusPending
import com.nhom2.booking.ui.format.StatusSuccess
import com.nhom2.booking.ui.format.formatVnd
import com.nhom2.booking.ui.theme.BookingBlue
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException

private const val REVENUE_MONTHS = 6

data class AdminStats(
    val numbers: Map<String, Double>,
    val revenueByMonth: List<Pair<String, Double>>,
) {
    fun count(key: String): Long = (numbers[key] ?: 0.0).toLong()
    fun amount(key: String): Double = numbers[key] ?: 0.0
}

class AdminOverviewViewModel(
    private val adminApi: AdminApi,
    private val appContext: Context,
) : EventViewModel() {

    private val _stats = MutableStateFlow<LoadState<AdminStats>>(LoadState.Loading)
    val stats: StateFlow<LoadState<AdminStats>> = _stats.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_stats.value !is LoadState.Success) _stats.value = LoadState.Loading
            try {
                val stats = coroutineScope {
                    val overview = async { apiCall { adminApi.getOverview() } }
                    val bookings = async { apiCall { adminApi.getBookings(null) } }
                    AdminStats(overview.await().toNumbers(), monthlyRevenue(bookings.await(), REVENUE_MONTHS))
                }
                _stats.value = LoadState.Success(stats)
            } catch (e: ApiException) {
                if (_stats.value is LoadState.Success) {
                    showError(e, R.string.admin_overview_load_failed)
                } else {
                    _stats.value = LoadState.Error(e.toUiText(UiText.Res(R.string.admin_overview_load_failed)))
                }
            }
        }
    }

    fun exportExcel() {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                val body = apiCall { adminApi.exportBookings() }
                val file = saveToCache(appContext, body, "bookings.xlsx")
                if (!openDocument(appContext, file, MIME_XLSX)) showMessage(R.string.no_app_to_open_file)
            } catch (e: ApiException) {
                showError(e, R.string.admin_export_failed)
            } catch (e: IOException) {
                showMessage(R.string.download_failed)
            } finally {
                _exporting.value = false
            }
        }
    }

    // paidRevenue is null when nothing is paid yet; every other value is a count.
    private fun JsonObject.toNumbers(): Map<String, Double> =
        mapNotNull { (key, value) -> (value as? JsonPrimitive)?.doubleOrNull?.let { key to it } }.toMap()
}

@Composable
fun AdminOverviewScreen(viewModel: AdminOverviewViewModel, onMessage: (UiText) -> Unit) {
    val state by viewModel.stats.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.admin_overview_title),
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    if (exporting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(12.dp).width(24.dp))
                    } else {
                        IconButton(onClick = viewModel::exportExcel) {
                            Icon(Icons.Filled.FileDownload, contentDescription = stringResource(R.string.admin_export_excel))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is LoadState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val stats = current.data
                StatTile(stringResource(R.string.admin_stat_revenue), formatVnd(stats.amount("paidRevenue")), BookingBlue)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.admin_stat_bookings), stats.count("totalBookings").toString(), BookingBlue, Modifier.weight(1f))
                    StatTile(stringResource(R.string.admin_stat_pending), stats.count("pendingBookings").toString(), StatusPending, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.admin_stat_confirmed), stats.count("confirmedBookings").toString(), StatusSuccess, Modifier.weight(1f))
                    StatTile(
                        stringResource(R.string.admin_stat_closed),
                        (stats.count("cancelledBookings") + stats.count("rejectedBookings")).toString(),
                        StatusError,
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.admin_stat_requests), stats.count("pendingRequests").toString(), StatusPending, Modifier.weight(1f))
                    StatTile(stringResource(R.string.admin_stat_users), stats.count("totalUsers").toString(), BookingBlue, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.admin_stat_hotels), stats.count("totalHotels").toString(), BookingBlue, Modifier.weight(1f))
                    StatTile(stringResource(R.string.admin_stat_rooms), stats.count("totalRooms").toString(), BookingBlue, Modifier.weight(1f))
                }
                RevenueChart(stats.revenueByMonth)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accent)
    }
}

/** Horizontal bars, one per month: enough to compare months without a charting library. */
@Composable
private fun RevenueChart(months: List<Pair<String, Double>>) {
    SectionCard {
        Text(stringResource(R.string.admin_revenue_chart), fontWeight = FontWeight.Bold)
        if (months.isEmpty()) {
            Text(stringResource(R.string.admin_revenue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val max = months.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
        months.forEach { (month, amount) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row {
                    Text(month.substring(5) + "/" + month.substring(0, 4), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(formatVnd(amount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((amount / max).toFloat())
                            .fillMaxHeight()
                            .background(BookingBlue, RoundedCornerShape(5.dp)),
                    )
                }
            }
        }
    }
}
