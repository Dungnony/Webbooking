package com.nhom2.booking.ui.auth

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.nhom2.booking.BuildConfig
import com.nhom2.booking.R
import com.nhom2.booking.ui.UiText
import kotlinx.coroutines.launch

private const val TAG = "GoogleSignIn"

/** Outcome of the Google account picker. */
sealed interface GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Failed(val message: UiText) : GoogleSignInResult
}

/**
 * Asks Google for an ID token issued to the web client the backend verifies (google.client-id). Google only
 * answers when this app's package name and signing SHA-1 are registered as an Android OAuth client in the same
 * Google Cloud project.
 */
suspend fun requestGoogleIdToken(activityContext: Context): GoogleSignInResult {
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
        .build()
    return try {
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleSignInResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleSignInResult.Failed(UiText.Res(R.string.google_failed))
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleSignInResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleSignInResult.Failed(UiText.Res(R.string.google_no_account))
    } catch (e: GetCredentialException) {
        Log.w(TAG, "Google sign-in failed", e)
        GoogleSignInResult.Failed(UiText.Res(R.string.google_unavailable))
    } catch (e: GoogleIdTokenParsingException) {
        Log.w(TAG, "Unreadable Google credential", e)
        GoogleSignInResult.Failed(UiText.Res(R.string.google_failed))
    }
}

@Composable
fun GoogleSignInButton(enabled: Boolean, onResult: (GoogleSignInResult) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = { scope.launch { onResult(requestGoogleIdToken(context)) } },
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 12.dp))
        Text(stringResource(R.string.google_sign_in))
    }
}
