package org.andrewdev.webwallet.feature.auth.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.ui.CodeField
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.auth.R
import org.andrewdev.webwallet.feature.auth.TwoFactorEffect
import org.andrewdev.webwallet.feature.auth.TwoFactorUiState
import org.andrewdev.webwallet.feature.auth.TwoFactorViewModel

@Composable
fun TwoFactorRoute(
    challenge: String,
    actions: AuthActions,
    onBack: () -> Unit,
    onExpired: () -> Unit,
    viewModel: TwoFactorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                TwoFactorEffect.Expired -> onExpired()
            }
        }
    }
    TwoFactorScreen(
        state = state,
        actions = actions,
        onCodeChange = viewModel::onCodeChange,
        onRecoveryCodeChange = viewModel::onRecoveryCodeChange,
        onTrustChange = viewModel::onTrustDeviceChange,
        onToggleRecovery = viewModel::toggleRecovery,
        onSubmit = { viewModel.submit(challenge) },
        onBack = onBack,
    )
}

@Composable
fun TwoFactorScreen(
    state: TwoFactorUiState,
    actions: AuthActions,
    onCodeChange: (String) -> Unit,
    onRecoveryCodeChange: (String) -> Unit,
    onTrustChange: (Boolean) -> Unit,
    onToggleRecovery: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    AuthScaffold(
        title = stringResource(R.string.feature_auth_two_factor_title),
        subtitle = stringResource(
            if (state.useRecovery) {
                R.string.feature_auth_two_factor_recovery_text
            } else {
                R.string.feature_auth_two_factor_app_text
            },
        ),
        onToggleLanguage = actions.toggleLanguage,
        onToggleTheme = actions.toggleTheme,
        footer = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.feature_auth_back_to_login)) }
        },
    ) {
        if (state.useRecovery) {
            OutlinedTextField(
                value = state.recoveryCode,
                onValueChange = onRecoveryCodeChange,
                label = { Text(stringResource(R.string.feature_auth_two_factor_recovery_code)) },
                isError = state.wrongCode,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CodeField(
                code = state.code,
                onCodeChange = onCodeChange,
                error = state.wrongCode,
                enabled = !state.submitting,
                onComplete = { onSubmit() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        state.error?.let {
            Text(
                it.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.trustDevice, onCheckedChange = onTrustChange)
            Text(stringResource(R.string.feature_auth_two_factor_trust), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_auth_two_factor_submit))
        }
        TextButton(onClick = onToggleRecovery, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(
                stringResource(
                    if (state.useRecovery) {
                        R.string.feature_auth_two_factor_use_app
                    } else {
                        R.string.feature_auth_two_factor_use_recovery
                    },
                ),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TwoFactorPreview() {
    WebWalletTheme {
        TwoFactorScreen(TwoFactorUiState(code = "123"), previewActions, {}, {}, {}, {}, {}, {})
    }
}
