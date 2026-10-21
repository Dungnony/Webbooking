package com.nhom2.booking.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.text.input.KeyboardType
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

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onMessage: (UiText) -> Unit,
    onRegistered: () -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = onRegistered)

    AuthScaffold(onBack = onBack) {
        when (state.step) {
            RegisterStep.FORM -> RegisterForm(state, viewModel)
            RegisterStep.OTP -> OtpStep(state, viewModel)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.auth_have_account), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onLogin) {
                Text(stringResource(R.string.auth_login_title), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ColumnScope.RegisterForm(state: RegisterUiState, viewModel: RegisterViewModel) {
    AuthTitle(stringResource(R.string.auth_register_title), stringResource(R.string.auth_register_subtitle))

    FormTextField(
        value = state.username,
        onValueChange = viewModel::onUsernameChange,
        label = stringResource(R.string.auth_username),
        error = state.usernameError,
        leadingIcon = Icons.Filled.Person,
    )
    FormTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = stringResource(R.string.auth_email),
        error = state.emailError,
        leadingIcon = Icons.Filled.Email,
        keyboardType = KeyboardType.Email,
    )
    PasswordTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.auth_password),
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
        onImeAction = viewModel::submitForm,
    )

    LoadingButton(stringResource(R.string.auth_register_now), state.loading, viewModel::submitForm)
}

@Composable
private fun ColumnScope.OtpStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .align(Alignment.CenterHorizontally),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
    Text(
        stringResource(R.string.auth_verify_title),
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Text(
        stringResource(R.string.auth_otp_sent_to, state.email),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    OtpTextField(
        value = state.otp,
        onValueChange = viewModel::onOtpChange,
        error = state.otpError,
        onDone = viewModel::submitOtp,
    )

    LoadingButton(stringResource(R.string.auth_verify_activate), state.loading, viewModel::submitOtp)
    TextButton(onClick = viewModel::backToForm, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.auth_back_to_edit), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
