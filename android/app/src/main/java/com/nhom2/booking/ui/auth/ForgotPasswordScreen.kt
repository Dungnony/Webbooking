package com.nhom2.booking.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom2.booking.R
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AuthScaffold
import com.nhom2.booking.ui.components.AuthTitle
import com.nhom2.booking.ui.components.FormTextField
import com.nhom2.booking.ui.components.LoadingButton
import com.nhom2.booking.ui.components.PasswordTextField
import com.nhom2.booking.ui.theme.SuccessGreen
import com.nhom2.booking.ui.theme.SuccessGreenContainer

@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onMessage: (UiText) -> Unit,
    onBackToLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = onBackToLogin)

    AuthScaffold(onBack = onBackToLogin) {
        when (state.step) {
            ForgotPasswordStep.USERNAME -> UsernameStep(state, viewModel, onBackToLogin)
            ForgotPasswordStep.RESET -> ResetStep(state, viewModel)
            ForgotPasswordStep.DONE -> DoneStep(onBackToLogin)
        }
    }
}

@Composable
private fun ColumnScope.UsernameStep(
    state: ForgotPasswordUiState,
    viewModel: ForgotPasswordViewModel,
    onBackToLogin: () -> Unit,
) {
    AuthTitle(stringResource(R.string.forgot_title), stringResource(R.string.forgot_step0_subtitle))
    FormTextField(
        value = state.username,
        onValueChange = viewModel::onUsernameChange,
        label = stringResource(R.string.auth_username),
        error = state.usernameError,
        leadingIcon = Icons.Filled.Person,
        imeAction = ImeAction.Done,
        onImeAction = viewModel::requestCode,
    )
    LoadingButton(stringResource(R.string.forgot_send_code), state.loading, viewModel::requestCode)
    TextButton(onClick = onBackToLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.forgot_back_to_login))
    }
}

@Composable
private fun ColumnScope.ResetStep(state: ForgotPasswordUiState, viewModel: ForgotPasswordViewModel) {
    AuthTitle(
        stringResource(R.string.forgot_title),
        stringResource(R.string.forgot_step1_subtitle, state.maskedEmail),
    )
    OtpTextField(
        value = state.otp,
        onValueChange = viewModel::onOtpChange,
        error = state.otpError,
        onDone = {},
        label = stringResource(R.string.forgot_otp_label),
    )
    PasswordTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.forgot_new_password),
        error = state.passwordError,
        leadingIcon = Icons.Filled.Lock,
    )
    PasswordTextField(
        value = state.confirm,
        onValueChange = viewModel::onConfirmChange,
        label = stringResource(R.string.auth_confirm_password),
        error = state.confirmError,
        leadingIcon = Icons.Filled.Lock,
        imeAction = ImeAction.Done,
        onImeAction = viewModel::resetPassword,
    )
    LoadingButton(stringResource(R.string.forgot_reset_submit), state.loading, viewModel::resetPassword)
    TextButton(onClick = viewModel::backToUsername, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.forgot_back))
    }
}

@Composable
private fun ColumnScope.DoneStep(onBackToLogin: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(SuccessGreenContainer, CircleShape)
            .align(Alignment.CenterHorizontally),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(36.dp))
    }
    Text(
        stringResource(R.string.forgot_success_title),
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Text(
        stringResource(R.string.forgot_success_body),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    LoadingButton(stringResource(R.string.forgot_login_now), loading = false, onClick = onBackToLogin)
}
