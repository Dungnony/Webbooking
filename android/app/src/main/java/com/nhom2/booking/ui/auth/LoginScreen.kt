package com.nhom2.booking.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
fun LoginScreen(
    viewModel: LoginViewModel,
    onMessage: (UiText) -> Unit,
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectUiEvents(viewModel.events, onMessage, onCompleted = onLoggedIn)

    AuthScaffold(onBack = onBack) {
        AuthTitle(stringResource(R.string.auth_login_title), stringResource(R.string.auth_login_subtitle))

        FormTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = stringResource(R.string.auth_username),
            error = state.usernameError,
            leadingIcon = Icons.Filled.Person,
        )
        PasswordTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_password),
            error = state.passwordError,
            leadingIcon = Icons.Filled.Lock,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submit,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onForgotPassword) { Text(stringResource(R.string.auth_forgot_password)) }
        }

        LoadingButton(stringResource(R.string.auth_login_title), state.loading, viewModel::submit)

        Text(
            stringResource(R.string.auth_or_sign_in_with),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        GoogleSignInButton(enabled = !state.loading, onResult = viewModel::onGoogleResult)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.auth_no_account), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRegister) {
                Text(stringResource(R.string.auth_register_free), fontWeight = FontWeight.Bold)
            }
        }
    }
}
