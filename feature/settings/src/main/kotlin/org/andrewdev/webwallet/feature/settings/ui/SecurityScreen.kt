package org.andrewdev.webwallet.feature.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.ui.CodeField
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.settings.R
import org.andrewdev.webwallet.feature.settings.SecurityUiState
import org.andrewdev.webwallet.feature.settings.SecurityViewModel
import org.andrewdev.webwallet.feature.settings.TwoFactorStep

/** Security (docs/04 §14): the password, the 2FA card and the app lock entry. */
@Composable
fun SecurityRoute(onBack: () -> Unit, onAppLock: () -> Unit, viewModel: SecurityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsPage(title = stringResource(R.string.feature_settings_security), onBack = onBack) {
        PasswordCard(state, viewModel)
        TwoFactorCard(state, viewModel)
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_app_lock)) },
            supportingContent = {
                Text(
                    stringResource(
                        if (state.lock.enabled) {
                            R.string.feature_settings_app_lock_on
                        } else {
                            R.string.feature_settings_app_lock_off
                        },
                    ),
                )
            },
            modifier = Modifier.clickable(onClick = onAppLock),
        )
    }
    state.recoveryCodes?.let { RecoveryCodesDialog(it, onDone = viewModel::codesSaved) }
    when (state.twoFactorStep) {
        TwoFactorStep.CONFIRM_IDENTITY -> ConfirmIdentityDialog(state, viewModel)
        TwoFactorStep.SCAN -> ScanDialog(state, viewModel)
        TwoFactorStep.DISABLE -> DisableDialog(state, viewModel)
        TwoFactorStep.REGENERATE -> RegenerateDialog(state, viewModel)
        TwoFactorStep.NONE -> Unit
    }
}

@Composable
private fun PasswordCard(state: SecurityUiState, viewModel: SecurityViewModel) {
    val user = state.user
    WwCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.feature_settings_password_title), style = MaterialTheme.typography.titleMedium)
            if (user != null && !user.hasPassword) {
                Text(
                    stringResource(R.string.feature_settings_password_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            SecretField(
                state.currentPassword,
                viewModel::onCurrentPasswordChange,
                stringResource(R.string.feature_settings_current_password),
            )
            SecretField(
                state.newPassword,
                viewModel::onNewPasswordChange,
                stringResource(R.string.feature_settings_new_password),
            )
            SecretField(
                state.repeatPassword,
                viewModel::onRepeatPasswordChange,
                stringResource(R.string.feature_settings_repeat_password),
                error = if (state.repeatPassword.isNotEmpty() && state.repeatPassword != state.newPassword) {
                    stringResource(
                        R.string.feature_settings_passwords_differ,
                    )
                } else {
                    null
                },
                imeAction = ImeAction.Done,
            )
            Text(
                stringResource(R.string.feature_settings_others_signed_out),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.passwordError?.let {
                Text(
                    it.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.passwordChanged) {
                Text(
                    stringResource(R.string.feature_settings_password_changed),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = viewModel::changePassword,
                enabled = !state.changingPassword && state.currentPassword.isNotEmpty() &&
                    state.newPassword.length >= MIN_PASSWORD && state.newPassword == state.repeatPassword,
            ) { Text(stringResource(R.string.feature_settings_change_password)) }
        }
    }
}

private const val MIN_PASSWORD = 8

@Composable
private fun TwoFactorCard(state: SecurityUiState, viewModel: SecurityViewModel) {
    val user = state.user ?: return
    WwCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(R.string.feature_settings_two_factor_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.feature_settings_two_factor_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (user.twoFactorEnabled) {
                Text(
                    stringResource(R.string.feature_settings_two_factor_on),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val left = user.recoveryCodesLeft.toInt()
                Text(
                    pluralStringResource(R.plurals.feature_settings_codes_left, left, left),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (left <= LOW_CODES) {
                        WwTheme.colors.warnText
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = viewModel::startRegenerate,
                    ) { Text(stringResource(R.string.feature_settings_new_codes)) }
                    TextButton(
                        onClick = viewModel::startDisable,
                    ) { Text(stringResource(R.string.feature_settings_two_factor_disable)) }
                }
            } else {
                Button(
                    onClick = viewModel::startSetup,
                ) { Text(stringResource(R.string.feature_settings_two_factor_enable)) }
            }
        }
    }
}

private const val LOW_CODES = 3

@Composable
private fun ConfirmIdentityDialog(state: SecurityUiState, viewModel: SecurityViewModel) {
    val hasPassword = state.user?.hasPassword ?: true
    androidx.compose.material3.AlertDialog(
        onDismissRequest = viewModel::cancelTwoFactor,
        title = { Text(stringResource(R.string.feature_settings_confirm_identity)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(
                        if (hasPassword) {
                            R.string.feature_settings_confirm_password_hint
                        } else {
                            R.string.feature_settings_confirm_code_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (hasPassword) {
                    SecretField(
                        state.identityPassword,
                        viewModel::onIdentityPasswordChange,
                        stringResource(R.string.feature_settings_current_password),
                        imeAction = ImeAction.Done,
                    )
                } else {
                    SecretField(
                        state.identityCode,
                        viewModel::onIdentityCodeChange,
                        stringResource(R.string.feature_settings_code_or_recovery),
                        imeAction = ImeAction.Done,
                        numeric = false,
                    )
                }
                state.twoFactorError?.let {
                    Text(
                        it.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::confirmIdentity,
                enabled = !state.working,
            ) { Text(stringResource(R.string.feature_settings_continue)) }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::cancelTwoFactor,
            ) { Text(stringResource(R.string.feature_settings_cancel)) }
        },
    )
}

/** The QR code of `otpauthUri`, the manual key and the first code (docs/03 §5). */
@Composable
private fun ScanDialog(state: SecurityUiState, viewModel: SecurityViewModel) {
    val setup = state.setup ?: return
    val context = LocalContext.current
    val bitmap = remember(setup.otpauthUri) { QrCodes.render(setup.otpauthUri, QR_SIZE) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = viewModel::cancelTwoFactor,
        title = { Text(stringResource(R.string.feature_settings_two_factor_setup)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.feature_settings_scan), style = MaterialTheme.typography.bodySmall)
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.feature_settings_qr),
                    modifier = Modifier.size(200.dp),
                )
                Text(stringResource(R.string.feature_settings_manual), style = MaterialTheme.typography.bodySmall)
                Text(setup.secret.chunked(KEY_GROUP).joinToString(" "), style = WwTheme.type.mono)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(
                        onClick = { copySensitive(context, setup.secret) },
                    ) { Text(stringResource(R.string.feature_settings_copy_key)) }
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(setup.otpauthUri)),
                            )
                        }
                    }) { Text(stringResource(R.string.feature_settings_open_authenticator)) }
                }
                CodeField(
                    code = state.code,
                    onCodeChange = viewModel::onCodeChange,
                    error = state.twoFactorError != null,
                    enabled = !state.working,
                    onComplete = { viewModel.enable() },
                )
                state.twoFactorError?.let {
                    Text(
                        it.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::enable,
                enabled = !state.working && state.code.length == SecurityViewModel.CODE_LENGTH,
            ) { Text(stringResource(R.string.feature_settings_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::cancelTwoFactor,
            ) { Text(stringResource(R.string.feature_settings_cancel)) }
        },
    )
}

private const val QR_SIZE = 512
private const val KEY_GROUP = 4

@Composable
private fun DisableDialog(state: SecurityUiState, viewModel: SecurityViewModel) {
    val hasPassword = state.user?.hasPassword ?: true
    androidx.compose.material3.AlertDialog(
        onDismissRequest = viewModel::cancelTwoFactor,
        title = { Text(stringResource(R.string.feature_settings_disable_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.feature_settings_disable_text), style = MaterialTheme.typography.bodySmall)
                SecretField(
                    state.code,
                    viewModel::onCodeChange,
                    stringResource(R.string.feature_settings_code_or_recovery),
                    numeric = false,
                )
                if (hasPassword) {
                    SecretField(
                        state.identityPassword,
                        viewModel::onIdentityPasswordChange,
                        stringResource(R.string.feature_settings_current_password),
                        imeAction = ImeAction.Done,
                    )
                }
                state.twoFactorError?.let {
                    Text(
                        it.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::disable,
                enabled = !state.working,
            ) {
                Text(
                    stringResource(R.string.feature_settings_two_factor_disable),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::cancelTwoFactor,
            ) { Text(stringResource(R.string.feature_settings_cancel)) }
        },
    )
}

@Composable
private fun RegenerateDialog(state: SecurityUiState, viewModel: SecurityViewModel) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = viewModel::cancelTwoFactor,
        title = { Text(stringResource(R.string.feature_settings_new_codes_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(R.string.feature_settings_new_codes_text),
                    style = MaterialTheme.typography.bodySmall,
                )
                SecretField(
                    state.code,
                    viewModel::onCodeChange,
                    stringResource(R.string.feature_settings_app_code),
                    imeAction = ImeAction.Done,
                    numeric = true,
                )
                state.twoFactorError?.let {
                    Text(
                        it.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::regenerate,
                enabled = !state.working,
            ) { Text(stringResource(R.string.feature_settings_new_codes_submit)) }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::cancelTwoFactor,
            ) { Text(stringResource(R.string.feature_settings_cancel)) }
        },
    )
}

/** The recovery codes, shown once: copy, share, and a checkbox before Done (docs/03 §5). */
@Composable
private fun RecoveryCodesDialog(codes: List<String>, onDone: () -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.feature_settings_recovery_codes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(R.string.feature_settings_codes_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(codes.joinToString("\n"), style = WwTheme.type.mono)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(
                        onClick = { copySensitive(context, codes.joinToString("\n")) },
                    ) { Text(stringResource(R.string.feature_settings_copy_all)) }
                    TextButton(onClick = {
                        val send = Intent(
                            Intent.ACTION_SEND,
                        ).setType("text/plain").putExtra(Intent.EXTRA_TEXT, codes.joinToString("\n"))
                        runCatching { context.startActivity(Intent.createChooser(send, null)) }
                    }) { Text(stringResource(R.string.feature_settings_share)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = saved, onCheckedChange = { saved = it })
                    Text(
                        stringResource(R.string.feature_settings_codes_saved),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDone,
                enabled = saved,
            ) { Text(stringResource(R.string.feature_settings_done)) }
        },
    )
}

/** Copies with the sensitive flag, so the clipboard preview does not show the secret (docs/03 §8). */
private fun copySensitive(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("WebWallet", text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    clipboard.setPrimaryClip(clip)
}
