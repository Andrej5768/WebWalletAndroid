package org.andrewdev.webwallet.feature.lock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.andrewdev.webwallet.core.auth.AppLock
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.ui.Logo
import org.andrewdev.webwallet.core.ui.PinDots
import org.andrewdev.webwallet.core.ui.PinKeypad
import org.andrewdev.webwallet.feature.lock.PinSetupStep
import org.andrewdev.webwallet.feature.lock.PinSetupUiState
import org.andrewdev.webwallet.feature.lock.PinSetupViewModel
import org.andrewdev.webwallet.feature.lock.R

@Composable
fun PinSetupRoute(change: Boolean, onClose: () -> Unit, viewModel: PinSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(change) { viewModel.start(change, biometricAvailable(context)) }
    LaunchedEffect(state.step, change) {
        // Changing the PIN has no options step: the old settings stay
        if (change && state.step == PinSetupStep.OPTIONS) viewModel.save(change = true, onDone = onClose)
    }
    PinSetupScreen(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onNext = viewModel::next,
        onBiometricChange = viewModel::onBiometricChange,
        onTimeoutChange = viewModel::onTimeoutChange,
        onDone = { viewModel.save(change = false, onDone = onClose) },
        onClose = onClose,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    state: PinSetupUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onNext: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                    ) { Icon(Lucide.X, contentDescription = stringResource(R.string.feature_lock_close)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.step == PinSetupStep.OPTIONS) {
                OptionsStep(state, onBiometricChange, onTimeoutChange, onDone)
            } else {
                PinStep(state, onDigit, onBackspace, onNext)
            }
        }
    }
}

@Composable
private fun ColumnScope.PinStep(
    state: PinSetupUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onNext: () -> Unit,
) {
    Spacer(Modifier.weight(1f))
    Logo()
    Spacer(Modifier.height(Spacing.lg))
    Text(
        stringResource(
            when (state.step) {
                PinSetupStep.CURRENT -> R.string.feature_lock_setup_current
                PinSetupStep.CREATE -> R.string.feature_lock_setup_create
                else -> R.string.feature_lock_setup_repeat
            },
        ),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.xl))
    PinDots(entered = state.pin.length, max = AppLock.MAX_PIN_LENGTH, error = state.mismatch || state.wrongCurrent)
    Spacer(Modifier.height(Spacing.md))
    val hint = when {
        state.wrongCurrent -> stringResource(R.string.feature_lock_wrong_pin)
        state.mismatch -> stringResource(R.string.feature_lock_setup_mismatch)
        state.weakWarning -> stringResource(R.string.feature_lock_setup_weak)
        else -> " "
    }
    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f))
    PinKeypad(onDigit = onDigit, onBackspace = onBackspace, enabled = !state.saving)
    Spacer(Modifier.height(Spacing.lg))
    Button(
        onClick = onNext,
        enabled = state.pin.length >= AppLock.MIN_PIN_LENGTH && !state.saving,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.feature_lock_setup_next))
    }
}

@Composable
private fun ColumnScope.OptionsStep(
    state: PinSetupUiState,
    onBiometricChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onDone: () -> Unit,
) {
    Text(stringResource(R.string.feature_lock_setup_options_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(Spacing.xl))
    if (state.biometricAvailable) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.feature_lock_biometric),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = state.biometric, onCheckedChange = onBiometricChange)
        }
        Spacer(Modifier.height(Spacing.lg))
    }
    Text(
        stringResource(R.string.feature_lock_setup_timeout),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Spacing.sm))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppLock.TIMEOUT_CHOICES_MINUTES.forEach { minutes ->
            FilterChip(
                selected = state.timeoutMinutes == minutes,
                onClick = { onTimeoutChange(minutes) },
                label = { Text(timeoutLabel(minutes)) },
            )
        }
    }
    Spacer(Modifier.weight(1f))
    Button(onClick = onDone, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.feature_lock_setup_done))
    }
}

@Composable
fun timeoutLabel(minutes: Int): String =
    if (minutes == 0) {
        stringResource(
            R.string.feature_lock_timeout_immediately,
        )
    } else {
        stringResource(R.string.feature_lock_timeout_minutes, minutes)
    }

/** "Protect the app with a PIN": shown once after the first sign-in on a device (docs/03 §6). */
@Composable
fun LockOfferScreen(onSetUp: () -> Unit, onNotNow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Logo()
        Spacer(Modifier.height(Spacing.lg))
        Text(
            stringResource(R.string.feature_lock_offer_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.feature_lock_offer_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxl))
        Button(onClick = onSetUp, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_lock_offer_setup))
        }
        TextButton(onClick = onNotNow) { Text(stringResource(R.string.feature_lock_offer_not_now)) }
    }
}

@PreviewLightDark
@Composable
private fun PinSetupPreview() {
    WebWalletTheme { PinSetupScreen(PinSetupUiState(pin = "123", weakWarning = true), {}, {}, {}, {}, {}, {}, {}) }
}

@PreviewLightDark
@Composable
private fun OptionsPreview() {
    WebWalletTheme {
        PinSetupScreen(
            PinSetupUiState(step = PinSetupStep.OPTIONS, biometricAvailable = true),
            {},
            {},
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
private fun LockOfferPreview() {
    WebWalletTheme { LockOfferScreen({}, {}) }
}
