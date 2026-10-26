package org.andrewdev.webwallet.feature.auth.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.auth.LinkState
import org.andrewdev.webwallet.feature.auth.R
import org.andrewdev.webwallet.feature.auth.RestoreViewModel
import org.andrewdev.webwallet.feature.auth.UnsubscribeViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The ISO instant of the problem as a long date in the UI language; the raw text when it cannot be read. */
@Composable
private fun deletionDate(iso: String): String {
    val date = formatters.date
    return runCatching {
        date.long(
            LocalDate.ofInstant(Instant.parse(iso), ZoneId.of("Europe/Kyiv")),
        )
    }.getOrDefault(iso)
}

/** After a sign-in into an account that is to be deleted (docs/03 §3.6). */
@Composable
fun PendingDeletionRoute(
    deletionScheduledAt: String,
    restoreToken: String,
    actions: AuthActions,
    onLeave: () -> Unit,
    onTwoFactor: (challenge: String) -> Unit,
    viewModel: RestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val challenge by viewModel.challenge.collectAsStateWithLifecycle()
    LaunchedEffect(challenge) { challenge?.let(onTwoFactor) }
    PendingDeletionScreen(deletionScheduledAt, state, actions, onRestore = {
        viewModel.restore(restoreToken)
    }, onLeave = onLeave)
}

@Composable
fun PendingDeletionScreen(
    deletionScheduledAt: String,
    state: LinkState,
    actions: AuthActions,
    onRestore: () -> Unit,
    onLeave: () -> Unit,
) {
    val date = deletionDate(deletionScheduledAt)
    AuthScaffold(
        title = stringResource(R.string.feature_auth_deletion_title, date),
        subtitle = null,
        onToggleLanguage = actions.toggleLanguage,
        onToggleTheme = actions.toggleTheme,
        footer = { TextButton(onClick = onLeave) { Text(stringResource(R.string.feature_auth_deletion_leave)) } },
    ) {
        Text(stringResource(R.string.feature_auth_deletion_text, date), style = MaterialTheme.typography.bodyMedium)
        when (state) {
            LinkState.Working -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            is LinkState.Failed -> Text(
                state.error.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            LinkState.Expired -> Text(
                stringResource(R.string.feature_auth_deletion_bad_link),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Unit
        }
        Button(onClick = onRestore, enabled = state != LinkState.Working, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_auth_deletion_restore))
        }
    }
}

/** The emailed restore link: cancels the deletion at once and sends the user to sign in (docs/03 §3.6). */
@Composable
fun RestoreAccountRoute(
    token: String,
    actions: AuthActions,
    onSignIn: () -> Unit,
    onTwoFactor: (challenge: String) -> Unit,
    viewModel: RestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val challenge by viewModel.challenge.collectAsStateWithLifecycle()
    LaunchedEffect(token) { viewModel.restore(token) }
    LaunchedEffect(challenge) { challenge?.let(onTwoFactor) }
    when (state) {
        LinkState.Done -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_deletion_restored_title),
            text = stringResource(R.string.feature_auth_deletion_restored_text),
            action = stringResource(R.string.feature_auth_sign_in_link),
            actions = actions,
            onAction = onSignIn,
        )
        LinkState.Expired -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_deletion_failed_title),
            text = stringResource(R.string.feature_auth_deletion_bad_link),
            action = stringResource(R.string.feature_auth_back_to_login),
            actions = actions,
            onAction = onSignIn,
            oops = true,
        )
        is LinkState.Failed -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_deletion_failed_title),
            text = (state as LinkState.Failed).error.message(),
            action = stringResource(R.string.feature_auth_back_to_login),
            actions = actions,
            onAction = onSignIn,
        )
        else -> AuthScaffold(
            title = stringResource(R.string.feature_auth_deletion_restored_title),
            subtitle = null,
            onToggleLanguage = actions.toggleLanguage,
            onToggleTheme = actions.toggleTheme,
        ) { CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
}

/** The public unsubscribe page of the weekly digest (docs/03 §7). */
@Composable
fun UnsubscribeRoute(
    user: String,
    token: String,
    actions: AuthActions,
    onSignIn: () -> Unit,
    viewModel: UnsubscribeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val again by viewModel.subscribedAgain.collectAsStateWithLifecycle()
    LaunchedEffect(user, token) { viewModel.unsubscribe(user, token) }
    when {
        again -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_unsubscribe_back_title),
            text = stringResource(R.string.feature_auth_unsubscribe_back_text),
            action = stringResource(R.string.feature_auth_sign_in_link),
            actions = actions,
            onAction = onSignIn,
        )
        state == LinkState.Done -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_unsubscribe_done_title),
            text = stringResource(R.string.feature_auth_unsubscribe_done_text),
            action = stringResource(R.string.feature_auth_unsubscribe_again),
            actions = actions,
            onAction = { viewModel.resubscribe(user, token) },
            secondaryAction = stringResource(R.string.feature_auth_sign_in_link),
            onSecondary = onSignIn,
        )
        state == LinkState.Expired || state is LinkState.Failed -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_unsubscribe_invalid),
            text = (state as? LinkState.Failed)?.error?.message()
                ?: stringResource(R.string.feature_auth_deletion_bad_link),
            action = stringResource(R.string.feature_auth_sign_in_link),
            actions = actions,
            onAction = onSignIn,
            oops = true,
        )
        else -> AuthScaffold(
            title = stringResource(R.string.feature_auth_unsubscribe_done_title),
            subtitle = null,
            onToggleLanguage = actions.toggleLanguage,
            onToggleTheme = actions.toggleTheme,
        ) { CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
}

/** "Help improve WebWallet": the one-time telemetry consent after the first sign-in (docs/04 §1, docs/08 §6). */
@Composable
fun ConsentScreen(onAnswer: (allow: Boolean) -> Unit, actions: AuthActions) {
    AuthScaffold(
        title = stringResource(R.string.feature_auth_consent_title),
        subtitle = null,
        onToggleLanguage = actions.toggleLanguage,
        onToggleTheme = actions.toggleTheme,
    ) {
        Text(stringResource(R.string.feature_auth_consent_text), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {
            onAnswer(true)
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.feature_auth_consent_allow)) }
        TextButton(onClick = { onAnswer(false) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.feature_auth_consent_not_now))
        }
    }
}

@PreviewLightDark
@Composable
private fun PendingDeletionPreview() {
    WebWalletTheme {
        PendingDeletionScreen("2026-11-09T10:00:00Z", LinkState.Idle, previewActions, {}, {})
    }
}

@PreviewLightDark
@Composable
private fun ConsentPreview() {
    WebWalletTheme { ConsentScreen({}, previewActions) }
}
