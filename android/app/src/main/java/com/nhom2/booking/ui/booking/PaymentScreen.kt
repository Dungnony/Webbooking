package com.nhom2.booking.ui.booking

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.nhom2.booking.R
import com.nhom2.booking.data.model.Booking
import com.nhom2.booking.data.model.PaymentQr
import com.nhom2.booking.domain.bookingCode
import com.nhom2.booking.domain.nights
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.asString
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.InfoRow
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.LoadingButton
import com.nhom2.booking.ui.components.SecondaryButton
import com.nhom2.booking.ui.components.SectionCard
import com.nhom2.booking.ui.format.bookingStatusLabel
import com.nhom2.booking.ui.format.formatDate
import com.nhom2.booking.ui.format.formatVnd

@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel,
    onMessage: (UiText) -> Unit,
    onDone: () -> Unit,
    onPayLater: () -> Unit,
    onBack: () -> Unit,
) {
    val bookingState by viewModel.booking.collectAsStateWithLifecycle()
    val qrState by viewModel.qr.collectAsStateWithLifecycle()
    val confirming by viewModel.confirming.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = onDone)

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.waitForPayment() }
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.payment_title), onBack = onBack) }) { padding ->
        when (val state = bookingState) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> ErrorBox(state.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is LoadState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StepIndicator(current = 1)
                Text(
                    stringResource(R.string.payment_intro, state.data.hotel?.name ?: stringResource(R.string.hotel_fallback_name)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QrSection(qrState)
                BookingSummary(state.data)
                WaitingNote()
                LoadingButton(stringResource(R.string.payment_manual_confirm), confirming, viewModel::confirmTransfer)
                SecondaryButton(stringResource(R.string.payment_pay_later), onPayLater, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun QrSection(state: LoadState<PaymentQr>) {
    SectionCard {
        Text(stringResource(R.string.payment_qr_title), fontWeight = FontWeight.Bold)
        when (state) {
            LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LoadState.Error -> Text(state.message.asString(), color = MaterialTheme.colorScheme.error)
            is LoadState.Success -> {
                val qr = state.data
                AsyncImage(
                    model = qr.qrCodeUrl,
                    contentDescription = stringResource(R.string.payment_qr_title),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(260.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(Color.White),
                )
                Text(
                    stringResource(R.string.payment_any_bank_app),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                TransferInfo(qr)
            }
        }
    }
}

@Composable
private fun TransferInfo(qr: PaymentQr) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InfoRow(stringResource(R.string.payment_bank), qr.bankName.orEmpty())
        CopyableRow(stringResource(R.string.payment_account_number), qr.accountNumber.orEmpty(), context)
        InfoRow(stringResource(R.string.payment_account_name), qr.accountName.orEmpty())
        CopyableRow(stringResource(R.string.payment_transfer_content), qr.transferContent.orEmpty(), context)
        InfoRow(stringResource(R.string.payment_amount), formatVnd(qr.amount), emphasize = true)
    }
}

@Composable
private fun CopyableRow(label: String, value: String, context: Context) {
    val copiedMessage = stringResource(R.string.payment_copied, label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfoRow(label, value, modifier = Modifier.weight(1f))
        IconButton(onClick = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = copiedMessage)
        }
    }
}

@Composable
private fun BookingSummary(booking: Booking) {
    val nights = booking.nights()
    SectionCard {
        Text(stringResource(R.string.booking_summary), fontWeight = FontWeight.Bold)
        InfoRow(stringResource(R.string.booking_code), bookingCode(booking.id))
        InfoRow(stringResource(R.string.booking_status), stringResource(bookingStatusLabel(booking.status)))
        InfoRow(stringResource(R.string.booking_check_in), formatDate(booking.checkInDate))
        InfoRow(stringResource(R.string.booking_check_out), formatDate(booking.checkOutDate))
        InfoRow(stringResource(R.string.booking_nights_label), pluralStringResource(R.plurals.nights, nights, nights))
        InfoRow(stringResource(R.string.booking_total), formatVnd(booking.totalPrice), emphasize = true)
    }
}

@Composable
private fun WaitingNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.payment_checking, PAYMENT_WINDOW_MINUTES), fontSize = 13.sp)
    }
}
