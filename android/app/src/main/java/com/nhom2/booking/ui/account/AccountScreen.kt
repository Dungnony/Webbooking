package com.nhom2.booking.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nhom2.booking.BuildConfig
import com.nhom2.booking.R
import com.nhom2.booking.data.session.Session
import com.nhom2.booking.data.session.SessionState
import com.nhom2.booking.data.settings.LanguageSettings
import com.nhom2.booking.domain.resolveImageUrl
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ConfirmDialog
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.LoadingButton
import com.nhom2.booking.ui.components.SecondaryButton
import com.nhom2.booking.ui.theme.BookingDarkBlue

/** Destinations reachable from the account tab. */
data class AccountActions(
    val onLogin: () -> Unit,
    val onRegister: () -> Unit,
    val onNotifications: () -> Unit,
    val onProfileSettings: () -> Unit,
    val onSupportChat: () -> Unit,
    val onAssistant: () -> Unit,
    val onManageUsers: () -> Unit,
    val onManageReviews: () -> Unit,
    val onSupportInbox: () -> Unit,
)

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    sessionState: SessionState,
    unreadNotifications: Int,
    unreadChat: Int,
    onMessage: (UiText) -> Unit,
    actions: AccountActions,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var chooseLanguage by rememberSaveable { mutableStateOf(false) }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::uploadAvatar)
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.tab_account)) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            when (sessionState) {
                SessionState.Loading -> LoadingBox()
                SessionState.LoggedOut -> GuestHeader(actions)
                is SessionState.LoggedIn -> {
                    val session = sessionState.session
                    ProfileHeader(
                        session = session,
                        email = profile?.email,
                        avatarUrl = resolveImageUrl(profile?.avatarUrl, BuildConfig.API_BASE_URL),
                        uploading = uploading,
                        onChangeAvatar = {
                            pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    MenuItem(Icons.Filled.Notifications, stringResource(R.string.notifications_title), unreadNotifications, actions.onNotifications)
                    MenuItem(Icons.Filled.Settings, stringResource(R.string.profile_settings_title), 0, actions.onProfileSettings)
                    if (session.isAdmin) {
                        HorizontalDivider()
                        SectionLabel(stringResource(R.string.admin_section))
                        MenuItem(Icons.Filled.SupportAgent, stringResource(R.string.admin_support_inbox), unreadChat, actions.onSupportInbox)
                        MenuItem(Icons.Filled.Group, stringResource(R.string.admin_users_title), 0, actions.onManageUsers)
                        MenuItem(Icons.Filled.RateReview, stringResource(R.string.admin_reviews_title), 0, actions.onManageReviews)
                    } else {
                        MenuItem(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.support_chat_title), unreadChat, actions.onSupportChat)
                    }
                }
            }
            HorizontalDivider()
            MenuItem(Icons.Filled.SmartToy, stringResource(R.string.assistant_title), 0, actions.onAssistant)
            MenuItem(Icons.Filled.Language, stringResource(R.string.language_title), 0) { chooseLanguage = true }
            if (sessionState is SessionState.LoggedIn) {
                MenuItem(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.logout), 0) { confirmLogout = true }
            }
            Text(
                stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.logout),
            text = stringResource(R.string.logout_confirm),
            confirmLabel = stringResource(R.string.logout),
            onConfirm = {
                viewModel.logout()
                confirmLogout = false
            },
            onDismiss = { confirmLogout = false },
        )
    }
    if (chooseLanguage) {
        LanguageDialog(onDismiss = { chooseLanguage = false })
    }
}

@Composable
private fun GuestHeader(actions: AccountActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BookingDarkBlue)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.account_guest_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.account_guest_body), color = Color.White.copy(alpha = 0.85f))
        LoadingButton(stringResource(R.string.auth_login_title), loading = false, onClick = actions.onLogin)
        Surface(color = Color.White, shape = MaterialTheme.shapes.small) {
            SecondaryButton(stringResource(R.string.auth_register_free), actions.onRegister, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ProfileHeader(
    session: Session,
    email: String?,
    avatarUrl: String?,
    uploading: Boolean,
    onChangeAvatar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BookingDarkBlue)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .clickable(enabled = !uploading, onClick = onChangeAvatar),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = stringResource(R.string.profile_avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (uploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = stringResource(R.string.profile_change_avatar),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(BookingDarkBlue, CircleShape)
                    .padding(4.dp)
                    .size(14.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.username, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(email ?: stringResource(R.string.profile_no_email), color = Color.White.copy(alpha = 0.85f))
            Text(
                stringResource(if (session.isAdmin) R.string.role_admin else R.string.role_user),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, badgeCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, modifier = Modifier.weight(1f))
        if (badgeCount > 0) Badge { Text(badgeCount.toString()) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = LanguageSettings.current(context)
    val options = listOf(
        LanguageSettings.VIETNAMESE to stringResource(R.string.language_vietnamese),
        LanguageSettings.ENGLISH to stringResource(R.string.language_english),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column {
                options.forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = code == current, onClick = {
                                onDismiss()
                                if (code != current) {
                                    LanguageSettings.save(context, code)
                                    // Recreating re-runs attachBaseContext, which applies the new language everywhere.
                                    context.findActivity()?.recreate()
                                }
                            })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = code == current, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
