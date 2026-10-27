package org.andrewdev.webwallet.core.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import timber.log.Timber

/** What asking Google for an account ended with. */
sealed interface GoogleIdTokenResult {
    data class Token(val idToken: String) : GoogleIdTokenResult

    data object Cancelled : GoogleIdTokenResult

    data class Failed(val message: String?) : GoogleIdTokenResult
}

/**
 * Asks Credential Manager for a Google account and returns its ID token (docs/03 §3.7). [serverClientId] is the
 * backend's web client, so the token's audience is the one the backend checks. Needs an activity context.
 */
suspend fun requestGoogleIdToken(context: Context, serverClientId: String): GoogleIdTokenResult {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(serverClientId)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        val isGoogleId = credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        if (credential is CustomCredential && isGoogleId) {
            GoogleIdTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleIdTokenResult.Failed(null)
        }
    } catch (e: GetCredentialCancellationException) {
        Timber.d(e, "Google sign-in cancelled")
        GoogleIdTokenResult.Cancelled
    } catch (e: GetCredentialException) {
        Timber.w(e, "Google sign-in failed")
        GoogleIdTokenResult.Failed(e.message)
    }
}
