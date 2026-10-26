package org.andrewdev.webwallet.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.andrewdev.webwallet.core.auth.AppLock
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.feature.settings.AppLockSettingsViewModel
import org.andrewdev.webwallet.feature.settings.R

/** Settings → App lock (docs/03 §6.3): enable, change PIN, biometrics, timeout, widgets, turn off. */
@Composable
fun AppLockSettingsRoute(
    biometricAvailable: Boolean,
    onBack: () -> Unit,
    onSetUp: () -> Unit,
    onChangePin: () -> Unit,
    viewModel: AppLockSettingsViewModel = hiltViewModel(),
) {
    val lock by viewModel.settings.collectAsStateWithLifecycle()
    var askPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    SettingsPage(title = stringResource(R.string.feature_settings_app_lock), onBack = onBack) {
        if (!lock.enabled) {
            Text(
                stringResource(R.string.feature_settings_app_lock_off_text),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSetUp,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feature_settings_app_lock_enable)) }
            return@SettingsPage
        }
        OutlinedButton(
            onClick = onChangePin,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.feature_settings_change_pin)) }
        if (biometricAvailable) {
            SwitchRow(stringResource(R.string.feature_settings_biometric), lock.biometric, viewModel::setBiometric)
        }
        Text(stringResource(R.string.feature_settings_timeout), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppLock.TIMEOUT_CHOICES_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = lock.timeoutMinutes == minutes,
                    onClick = { viewModel.setTimeout(minutes) },
                    label = {
                        Text(
                            if (minutes == 0) {
                                stringResource(
                                    R.string.feature_settings_timeout_immediately,
                                )
                            } else {
                                stringResource(R.string.feature_settings_timeout_minutes, minutes)
                            },
                        )
                    },
                )
            }
        }
        SwitchRow(
            stringResource(R.string.feature_settings_widgets_balances),
            lock.showBalancesInWidgets,
            viewModel::setShowBalancesInWidgets,
        )
        TextButton(onClick = {
            askPin = true
            pin = ""
            wrong = false
        }) {
            Text(stringResource(R.string.feature_settings_app_lock_disable), color = MaterialTheme.colorScheme.error)
        }
    }
    if (askPin) {
        AlertDialog(
            onDismissRequest = { askPin = false },
            title = { Text(stringResource(R.string.feature_settings_app_lock_disable)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecretField(
                        pin,
                        {
                            pin = it.filter(Char::isDigit)
                            wrong = false
                        },
                        stringResource(R.string.feature_settings_current_pin),
                        error = if (wrong) stringResource(R.string.feature_settings_wrong_pin) else null,
                        imeAction = ImeAction.Done,
                        numeric = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disable(pin) { wrong = true }
                        if (!wrong) askPin = false
                    },
                    enabled = pin.length >= AppLock.MIN_PIN_LENGTH,
                ) {
                    Text(stringResource(R.string.feature_settings_app_lock_disable))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { askPin = false },
                ) { Text(stringResource(R.string.feature_settings_cancel)) }
            },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
