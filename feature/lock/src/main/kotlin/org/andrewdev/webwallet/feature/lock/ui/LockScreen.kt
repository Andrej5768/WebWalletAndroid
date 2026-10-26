package org.andrewdev.webwallet.feature.lock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Fingerprint
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.auth.AppLock
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.Logo
import org.andrewdev.webwallet.core.ui.PinDots
import org.andrewdev.webwallet.core.ui.PinKeypad
import org.andrewdev.webwallet.feature.lock.LockUiState
import org.andrewdev.webwallet.feature.lock.LockViewModel
import org.andrewdev.webwallet.feature.lock.R

@Composable
fun LockRoute(viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val title = stringResource(R.string.feature_lock_biometric_title)
    val negative = stringResource(R.string.feature_lock_biometric_negative)
    var biometricMessage by remember { mutableStateOf<String?>(null) }
    val prompt = {
        if (activity != null) {
            showBiometricPrompt(activity, title, negative, onSuccess = viewModel::onBiometricSuccess, onFailed = {
                biometricMessage = it
            })
        }
    }
    // The prompt opens by itself when biometrics are on (docs/03 §6.2)
    LaunchedEffect(state.biometricEnabled) { if (state.biometricEnabled) prompt() }
    LockScreen(
        state = state,
        biometricMessage = biometricMessage,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onSubmit = viewModel::submit,
        onBiometric = prompt,
        onSignOut = viewModel::signOut,
    )
}

@Composable
@Suppress("CyclomaticComplexMethod")
fun LockScreen(
    state: LockUiState,
    biometricMessage: String?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onBiometric: () -> Unit,
    onSignOut: () -> Unit,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Logo()
        Spacer(Modifier.height(Spacing.lg))
        state.displayName?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(R.string.feature_lock_enter_pin), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xl))
        PinDots(entered = state.pin.length, max = AppLock.MAX_PIN_LENGTH, error = state.wrong)
        Spacer(Modifier.height(Spacing.md))
        val hint = when {
            state.cooldownSeconds > 0 -> stringResource(R.string.feature_lock_cooldown, state.cooldownSeconds.toInt())
            state.wrong && state.attemptsLeft != null && state.attemptsLeft > 0 ->
                stringResource(R.string.feature_lock_wrong_pin) + ". " +
                    pluralStringResource(R.plurals.feature_lock_attempts_left, state.attemptsLeft, state.attemptsLeft)
            state.wrong -> stringResource(R.string.feature_lock_wrong_pin)
            biometricMessage != null -> biometricMessage
            else -> " "
        }
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PinKeypad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            enabled = !state.checking && state.cooldownSeconds == 0L && !state.signingOut,
            extraIcon = if (state.biometricEnabled) Lucide.Fingerprint else null,
            extraDescription = stringResource(R.string.feature_lock_biometric),
            onExtra = onBiometric,
        )
        Spacer(Modifier.height(Spacing.lg))
        Button(
            onClick = onSubmit,
            enabled = state.pin.length >= AppLock.MIN_PIN_LENGTH && !state.checking && state.cooldownSeconds == 0L,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.feature_lock_unlock)) }
        TextButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.feature_lock_forgot)) }
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.feature_lock_forgot_title),
            body = stringResource(R.string.feature_lock_forgot_text),
            confirmText = stringResource(R.string.feature_lock_sign_out),
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@PreviewLightDark
@Composable
private fun LockPreview() {
    WebWalletTheme {
        LockScreen(
            LockUiState(pin = "12", biometricEnabled = true, displayName = "Ada Lovelace"),
            null,
            {},
            {},
            {},
            {},
            {},
        )
    }
}

@PreviewLightDark
@Composable
private fun LockWrongPreview() {
    WebWalletTheme {
        LockScreen(LockUiState(wrong = true, attemptsLeft = 2), null, {}, {}, {}, {}, {})
    }
}
