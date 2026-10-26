package org.andrewdev.webwallet.feature.auth.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.navigation.CheckMailPurpose
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.auth.CheckMailViewModel
import org.andrewdev.webwallet.feature.auth.ForgotPasswordViewModel
import org.andrewdev.webwallet.feature.auth.LinkState
import org.andrewdev.webwallet.feature.auth.R
import org.andrewdev.webwallet.feature.auth.ResetPasswordUiState
import org.andrewdev.webwallet.feature.auth.ResetPasswordViewModel
import org.andrewdev.webwallet.feature.auth.VerifyEmailViewModel

/** The message of a [LinkState] for the small screens that have one request. */
@Composable
private fun LinkStatus(state: LinkState, doneText: String? = null) {
    when (state) {
        LinkState.Working -> CircularProgressIndicator()
        LinkState.Done -> if (doneText != null) {
            Text(
                doneText,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is LinkState.Failed -> Text(
            state.error.message(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        LinkState.Expired, LinkState.Idle -> Unit
    }
}

// --- Check mail (docs/03 §3.3)

@Composable
fun CheckMailRoute(
    email: String,
    purpose: CheckMailPurpose,
    actions: AuthActions,
    onBack: () -> Unit,
    viewModel: CheckMailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CheckMailScreen(email, purpose, state, actions, onResend = { viewModel.resend(email) }, onBack = onBack)
}

@Composable
fun CheckMailScreen(
    email: String,
    purpose: CheckMailPurpose,
    resend: LinkState,
    actions: AuthActions,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    AuthScaffold(
        title = stringResource(R.string.feature_auth_check_mail_title),
        subtitle = null,
        onToggleLanguage = actions.toggleLanguage,
        onToggleTheme = actions.toggleTheme,
        footer = { TextButton(onClick = onBack) { Text(stringResource(R.string.feature_auth_back_to_login)) } },
    ) {
        Text(
            stringResource(
                if (purpose == CheckMailPurpose.REGISTERED) {
                    R.string.feature_auth_check_mail_registered
                } else {
                    R.string.feature_auth_check_mail_reset
                },
                email,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.feature_auth_check_mail_open)) }
        if (purpose == CheckMailPurpose.REGISTERED) {
            Text(
                stringResource(R.string.feature_auth_check_mail_no_mail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onResend,
                enabled = resend != LinkState.Working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_auth_check_mail_resend))
            }
            LinkStatus(resend, stringResource(R.string.feature_auth_check_mail_resent))
        }
    }
}

// --- Forgot password (docs/03 §3.5)

@Composable
fun ForgotPasswordRoute(
    actions: AuthActions,
    onBack: () -> Unit,
    onSent: (email: String) -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.link) { if (state.link == LinkState.Done) onSent(state.email.trim().lowercase()) }
    AuthScaffold(
        title = stringResource(R.string.feature_auth_forgot_title),
        subtitle = stringResource(R.string.feature_auth_forgot_subtitle),
        onToggleLanguage = actions.toggleLanguage,
        onToggleTheme = actions.toggleTheme,
        footer = { TextButton(onClick = onBack) { Text(stringResource(R.string.feature_auth_back_to_login)) } },
    ) {
        EmailField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            imeAction = ImeAction.Done,
            onDone = viewModel::submit,
        )
        LinkStatus(state.link)
        Button(
            onClick = viewModel::submit,
            enabled = state.link != LinkState.Working && state.email.contains('@'),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feature_auth_forgot_submit))
        }
    }
}

// --- Reset password (docs/03 §3.5)

@Composable
fun ResetPasswordRoute(
    token: String,
    actions: AuthActions,
    onDone: () -> Unit,
    onRequestNew: () -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResetPasswordScreen(state, actions, viewModel::onPasswordChange, viewModel::onRepeatChange, {
        viewModel.submit(token)
    }, onDone, onRequestNew)
}

@Composable
fun ResetPasswordScreen(
    state: ResetPasswordUiState,
    actions: AuthActions,
    onPasswordChange: (String) -> Unit,
    onRepeatChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDone: () -> Unit,
    onRequestNew: () -> Unit,
) {
    when (state.link) {
        LinkState.Done -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_reset_done_title),
            text = stringResource(R.string.feature_auth_reset_done_text),
            action = stringResource(R.string.feature_auth_sign_in_link),
            actions = actions,
            onAction = onDone,
        )
        LinkState.Expired -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_expired_title),
            text = stringResource(R.string.feature_auth_reset_expired),
            action = stringResource(R.string.feature_auth_reset_request_new),
            actions = actions,
            onAction = onRequestNew,
            oops = true,
        )
        else -> AuthScaffold(
            title = stringResource(R.string.feature_auth_reset_title),
            subtitle = stringResource(R.string.feature_auth_reset_subtitle),
            onToggleLanguage = actions.toggleLanguage,
            onToggleTheme = actions.toggleTheme,
        ) {
            PasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.feature_auth_new_password),
                error = passwordProblemText(state.passwordProblem),
                supporting = stringResource(R.string.feature_auth_password_hint),
                newPassword = true,
                imeAction = ImeAction.Next,
            )
            PasswordField(
                value = state.repeat,
                onValueChange = onRepeatChange,
                label = stringResource(R.string.feature_auth_repeat_password),
                error = if (state.mismatch) stringResource(R.string.feature_auth_passwords_differ) else null,
                newPassword = true,
                onDone = onSubmit,
            )
            LinkStatus(state.link)
            Button(onClick = onSubmit, enabled = state.link != LinkState.Working, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_auth_reset_submit))
            }
        }
    }
}

// --- Verify email (docs/03 §3.4)

@Composable
fun VerifyEmailRoute(
    token: String?,
    actions: AuthActions,
    onSignIn: () -> Unit,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resend by viewModel.resend.collectAsStateWithLifecycle()
    LaunchedEffect(token) { viewModel.verify(token) }
    var email by rememberSaveable { mutableStateOf("") }
    when (state) {
        LinkState.Done -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_verify_done_title),
            text = stringResource(R.string.feature_auth_verify_done_text),
            action = stringResource(R.string.feature_auth_sign_in_link),
            actions = actions,
            onAction = onSignIn,
        )
        LinkState.Expired -> AuthScaffold(
            title = stringResource(R.string.feature_auth_expired_title),
            subtitle = stringResource(R.string.feature_auth_verify_expired),
            onToggleLanguage = actions.toggleLanguage,
            onToggleTheme = actions.toggleTheme,
            footer = { TextButton(onClick = onSignIn) { Text(stringResource(R.string.feature_auth_back_to_login)) } },
        ) {
            EmailField(value = email, onValueChange = {
                email = it
            }, imeAction = ImeAction.Done, onDone = { viewModel.resend(email) })
            LinkStatus(resend, stringResource(R.string.feature_auth_check_mail_resent))
            Button(onClick = {
                viewModel.resend(email)
            }, enabled = email.contains('@') && resend != LinkState.Working, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_auth_check_mail_resend))
            }
        }
        is LinkState.Failed -> OutcomeScreen(
            title = stringResource(R.string.feature_auth_expired_oops),
            text = (state as LinkState.Failed).error.message(),
            action = stringResource(R.string.feature_auth_back_to_login),
            actions = actions,
            onAction = onSignIn,
        )
        else -> AuthScaffold(
            title = stringResource(R.string.feature_auth_verify_working),
            subtitle = null,
            onToggleLanguage = actions.toggleLanguage,
            onToggleTheme = actions.toggleTheme,
        ) { CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
}

/** A title, a sentence and one button: the end of a link flow. */
@Composable
fun OutcomeScreen(
    title: String,
    text: String,
    action: String,
    actions: AuthActions,
    onAction: () -> Unit,
    oops: Boolean = false,
    secondaryAction: String? = null,
    onSecondary: () -> Unit = {},
) {
    AuthScaffold(
        title = if (oops) stringResource(R.string.feature_auth_expired_oops) else title,
        subtitle = if (oops) title else null,
        onToggleLanguage = actions.toggleLanguage,
        onToggleTheme = actions.toggleTheme,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
        if (secondaryAction != null) {
            TextButton(
                onClick = onSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(secondaryAction) }
        }
    }
}

@PreviewLightDark
@Composable
private fun CheckMailPreview() {
    WebWalletTheme {
        CheckMailScreen("ada@example.com", CheckMailPurpose.REGISTERED, LinkState.Done, previewActions, {}, {})
    }
}

@PreviewLightDark
@Composable
private fun ResetPasswordPreview() {
    WebWalletTheme {
        ResetPasswordScreen(
            ResetPasswordUiState(
                password = "longenough1",
                repeat = "x",
                validated = true,
            ),
            previewActions,
            {
            },
            {},
            {},
            {},
            {},
        )
    }
}
