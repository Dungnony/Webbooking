package com.nhom2.booking.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom2.booking.R
import com.nhom2.booking.domain.OTP_LENGTH
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.asString

/** Keeps only digits, up to [OTP_LENGTH], so pasted codes like "123 456" still work. */
fun sanitizeOtp(input: String): String = input.filter { it.isDigit() }.take(OTP_LENGTH)

/** Message for an OTP that failed [com.nhom2.booking.domain.isValidOtp]. */
fun otpInputError(otp: String): UiText =
    if (otp.isEmpty()) UiText.Res(R.string.auth_otp_required) else UiText.Res(R.string.forgot_otp_invalid)

@Composable
fun OtpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    error: UiText?,
    onDone: () -> Unit,
    label: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = {
            Text("• • • • • •", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 24.sp)
        },
        textStyle = TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 12.sp,
            textAlign = TextAlign.Center,
        ),
        isError = error != null,
        supportingText = error?.let { { Text(it.asString()) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
