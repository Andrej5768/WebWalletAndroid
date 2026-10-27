package org.andrewdev.webwallet.feature.auth.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ErrorCode
import org.andrewdev.webwallet.core.ui.GoogleIdTokenResult
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.core.ui.requestGoogleIdToken
import org.andrewdev.webwallet.feature.auth.R
import org.andrewdev.webwallet.feature.auth.SignInEffect
import org.andrewdev.webwallet.feature.auth.SignInUiState
import org.andrewdev.webwallet.feature.auth.SignInViewModel

/** A one-line note above the form: why the user is back here (session expired, password changed, …). */
enum class SignInNotice { SESSION_EXPIRED, CODE_EXPIRED, PASSWORD_CHANGED, VERIFIED, RESTORED }

/** What the sign-in screen needs from the host: the toggles and where to go next. */
class AuthActions(
    val toggleLanguage: () -> Unit,
    val toggleTheme: () -> Unit,
    val openRegister: () -> Unit,
    val openForgotPassword: () -> Unit,
    val openTwoFactor: (challenge: String) -> Unit,
    val openPendingDeletion: (date: String, restoreToken: String) -> Unit,
    val openCheckMail: (email: String) -> Unit,
)

@Composable
fun SignInRoute(
    actions: AuthActions,
    notice: SignInNotice?,
    prefillEmail: String?,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleUnavailable = stringResource(R.string.feature_auth_login_google_unavailable)
    LaunchedEffect(prefillEmail) { prefillEmail?.let(viewModel::prefill) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SignInEffect.TwoFactor -> actions.openTwoFactor(effect.challenge)
                is SignInEffect.PendingDeletion -> actions.openPendingDeletion(
                    effect.deletionScheduledAt,
                    effect.restoreToken,
                )
                is SignInEffect.CheckMail -> actions.openCheckMail(effect.email)
            }
        }
    }
    SignInScreen(
        state = state,
        notice = notice,
        actions = actions,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onResend = viewModel::resendVerification,
        onDemo = viewModel::startDemo,
        onGoogle = {
            viewModel.setGoogleError(null)
            viewModel.googleWebClientId { clientId ->
                if (clientId == null) {
                    viewModel.setGoogleError(googleUnavailable)
                } else {
                    scope.launch {
                        when (val result = requestGoogleIdToken(context, clientId)) {
                            is GoogleIdTokenResult.Token -> viewModel.signInWithGoogle(result.idToken)
                            GoogleIdTokenResult.Cancelled -> Unit
                            is GoogleIdTokenResult.Failed -> viewModel.setGoogleError(googleUnavailable)
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun SignInScreen(
    state: SignInUiState,
    notice: SignInNotice?,
    actions: AuthActions,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onDemo: () -> Unit = {},
    onGoogle: () -> Unit = {},
) {
    AuthScaffold(
        title = stringResource(R.string.feature_auth_login_title),
        subtitle = stringResource(R.string.feature_auth_login_subtitle),
        onToggleLanguage = actions.toggleLanguage,
        onToggleTheme = actions.toggleTheme,
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_auth_login_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = actions.openRegister,
                ) { Text(stringResource(R.string.feature_auth_login_register)) }
            }
        },
    ) {
        notice?.let {
            Text(
                noticeText(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        EmailField(
            value = state.email,
            onValueChange = onEmailChange,
            error = if (state.emailError) stringResource(R.string.feature_auth_email_invalid) else null,
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.feature_auth_password),
            error = if (state.passwordError) state.error?.message() else null,
            imeAction = ImeAction.Done,
            onDone = onSubmit,
        )
        state.error?.takeIf { !state.passwordError }?.let { error ->
            WarnCard(
                text = error.message(),
                action = if (state.notVerified) stringResource(R.string.feature_auth_login_resend) else null,
                onAction = onResend,
            )
        }
        Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (state.cooldownSeconds > 0) {
                    stringResource(R.string.feature_auth_login_wait, state.cooldownSeconds)
                } else {
                    stringResource(R.string.feature_auth_login_submit)
                },
            )
        }
        TextButton(onClick = actions.openForgotPassword, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.feature_auth_login_forgot))
        }
        if (state.googleOffered) {
            OutlinedButton(
                onClick = onGoogle,
                enabled = !state.signingInWithGoogle && !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_auth_login_google))
            }
            state.googleError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.demoOffered) {
            OutlinedButton(
                onClick = onDemo,
                enabled = !state.startingDemo && !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_auth_login_demo))
            }
            Text(
                stringResource(R.string.feature_auth_login_demo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun noticeText(notice: SignInNotice): String = stringResource(
    when (notice) {
        SignInNotice.SESSION_EXPIRED -> R.string.feature_auth_login_session_expired
        SignInNotice.CODE_EXPIRED -> R.string.feature_auth_login_code_expired
        SignInNotice.PASSWORD_CHANGED -> R.string.feature_auth_login_password_changed
        SignInNotice.VERIFIED -> R.string.feature_auth_login_verified
        SignInNotice.RESTORED -> R.string.feature_auth_login_restored
    },
)

internal val previewActions = AuthActions({}, {}, {}, {}, {}, { _, _ -> }, {})

@PreviewLightDark
@Composable
private fun SignInPreview() {
    WebWalletTheme {
        SignInScreen(
            SignInUiState(email = "ada@example.com"),
            SignInNotice.SESSION_EXPIRED,
            previewActions,
            {},
            {},
            {},
            {},
        )
    }
}

@PreviewLightDark
@Composable
@Suppress("MagicNumber")
private fun SignInErrorPreview() {
    WebWalletTheme {
        SignInScreen(
            SignInUiState(
                email = "ada@example.com",
                password = "x",
                error = ApiError.Problem(
                    403,
                    ErrorCode.EMAIL_NOT_VERIFIED,
                    "",
                    "Підтвердіть пошту",
                    emptyMap(),
                    kotlinx.serialization.json.JsonObject(emptyMap()),
                ),
                notVerified = true,
            ),
            null,
            previewActions,
            {},
            {},
            {},
            {},
        )
    }
}
