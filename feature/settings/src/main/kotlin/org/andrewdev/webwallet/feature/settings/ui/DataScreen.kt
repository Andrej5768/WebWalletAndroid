package org.andrewdev.webwallet.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.settings.DataUiState
import org.andrewdev.webwallet.feature.settings.DataViewModel
import org.andrewdev.webwallet.feature.settings.R

@Composable
fun DataRoute(onBack: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var archive by remember { mutableStateOf<ByteArray?>(null) }
    val resetDone = stringResource(R.string.feature_settings_data_reset_done)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val bytes = archive ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        }
        archive = null
    }
    DataScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onExport = {
            viewModel.export { result ->
                when (result) {
                    is ApiResult.Ok -> {
                        archive = result.value
                        saver.launch(EXPORT_FILE)
                    }
                    is ApiResult.Err -> showError(result.error)
                }
            }
        },
        onReset = { confirm, keep ->
            viewModel.reset(
                confirm,
                keep,
            ) { scope.launch { snackbar.showSnackbar(resetDone) } }
        },
        onDelete = viewModel::deleteAccount,
        onSignedOut = viewModel::signOut,
        onClearError = viewModel::clearError,
    )
}

/** The data of the account (docs/04 §14 Data): the export, starting over, the deletion with a 14-day notice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    state: DataUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onReset: (confirm: String, keepCategories: Boolean) -> Unit,
    onDelete: (confirmEmail: String, password: String?, code: String?) -> Unit,
    onSignedOut: () -> Unit,
    onClearError: () -> Unit,
) {
    var resetting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val f = formatters
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_data)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(
                padding,
            ).verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            WwCard {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.feature_settings_data_export_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.feature_settings_data_export_text),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.feature_settings_data_export_secrets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onExport, enabled = !state.exporting) {
                        Text(stringResource(R.string.feature_settings_data_export_button))
                    }
                }
            }
            Text(stringResource(R.string.feature_settings_danger_title), style = MaterialTheme.typography.titleSmall)
            WwCard {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.feature_settings_danger_reset_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.feature_settings_danger_reset_text),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { resetting = true }, enabled = !state.resetting) {
                        Text(stringResource(R.string.feature_settings_danger_reset_button))
                    }
                }
            }
            WwCard {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.feature_settings_danger_delete_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.feature_settings_danger_delete_text),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { deleting = true },
                        enabled = !state.deleting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.feature_settings_danger_delete_button)) }
                }
            }
        }
    }
    if (resetting) {
        ResetSheet(
            error = state.error?.message(),
            busy = state.resetting,
            onSubmit = { confirm, keep -> onReset(confirm, keep) },
            onDismiss = {
                resetting = false
                onClearError()
            },
        )
    }
    if (deleting && state.deletionScheduledAt == null) {
        DeleteSheet(
            byCode = state.user?.twoFactorEnabled == true || state.user?.hasPassword == false,
            error = state.error?.message(),
            busy = state.deleting,
            onSubmit = onDelete,
            onDismiss = {
                deleting = false
                onClearError()
            },
        )
    }
    state.deletionScheduledAt?.let { at ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.feature_settings_delete_done_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.feature_settings_delete_done_text,
                        f.date.long(at.toLocalDate()),
                        state.user?.email.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onSignedOut) { Text(stringResource(R.string.feature_settings_delete_ok)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResetSheet(error: String?, busy: Boolean, onSubmit: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.feature_settings_danger_reset_title),
                style = MaterialTheme.typography.titleMedium,
            )
            WarnCard(text = stringResource(R.string.feature_settings_danger_reset_warning))
            SwitchRow(
                title = stringResource(R.string.feature_settings_danger_keep_categories),
                hint = null,
                checked = keep,
                onChange = { keep = it },
            )
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.feature_settings_danger_confirm_label, CONFIRM_WORD)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onSubmit(typed.trim(), keep) },
                enabled = typed.trim().equals(CONFIRM_WORD, ignoreCase = true) && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feature_settings_danger_reset_submit)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteSheet(
    byCode: Boolean,
    error: String?,
    busy: Boolean,
    onSubmit: (confirmEmail: String, password: String?, code: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.feature_settings_delete_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.feature_settings_delete_blocked), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.feature_settings_delete_cancel), style = MaterialTheme.typography.bodySmall)
            WarnCard(text = stringResource(R.string.feature_settings_delete_export_first))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.feature_settings_delete_confirm_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = {
                    Text(
                        stringResource(
                            if (byCode) {
                                R.string.feature_settings_app_code
                            } else {
                                R.string.feature_settings_current_password
                            },
                        ),
                    )
                },
                visualTransformation = if (byCode) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onSubmit(email.trim(), secret.takeIf { !byCode }, secret.takeIf { byCode }) },
                enabled = email.contains('@') && secret.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feature_settings_danger_delete_button)) }
        }
    }
}

private const val CONFIRM_WORD = "DELETE"
private const val EXPORT_FILE = "webwallet-export.zip"
