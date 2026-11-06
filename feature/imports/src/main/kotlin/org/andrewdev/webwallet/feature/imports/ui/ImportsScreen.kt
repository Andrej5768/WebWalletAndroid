package org.andrewdev.webwallet.feature.imports.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.model.MonobankConnection
import org.andrewdev.webwallet.core.model.MonobankLink
import org.andrewdev.webwallet.core.model.MonobankSchedule
import org.andrewdev.webwallet.core.model.SyncMode
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.model.UploadFile
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.DatePickerDialogOf
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.imports.ConnectStep
import org.andrewdev.webwallet.feature.imports.FileSource
import org.andrewdev.webwallet.feature.imports.ImportsUiState
import org.andrewdev.webwallet.feature.imports.ImportsViewModel
import org.andrewdev.webwallet.feature.imports.PreviewReady
import org.andrewdev.webwallet.feature.imports.R
import org.andrewdev.webwallet.feature.imports.SourceForms
import org.andrewdev.webwallet.feature.imports.ZenMode
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate

private const val MONOBANK_SITE = "https://api.monobank.ua/"
private const val HISTORY_YEARS = 3L
private val PDF = arrayOf("application/pdf")
private val CSV = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "text/plain",
    "application/json",
)
private val XLSX = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/octet-stream",
)

/** Where the screen leads. */
data class ImportsActions(
    val back: () -> Unit,
    val openPreview: (PreviewReady) -> Unit,
    /** The editor with the type, the account and the amount of a difference to the bank. */
    val addOperation: (TransactionType, Long, BigDecimal) -> Unit,
)

/** Everything the screen can ask the view model for. */
@Suppress("LongParameterList")
private class ImportsCallbacks(
    val refresh: () -> Unit,
    val openConnect: () -> Unit,
    val closeConnect: () -> Unit,
    val toggleManual: () -> Unit,
    val connect: (token: String, since: LocalDate, schedule: MonobankSchedule) -> Unit,
    val openAcceptUrl: (String) -> Unit,
    val editSchedule: (MonobankSchedule) -> Unit,
    val saveSchedule: () -> Unit,
    val syncNow: () -> Unit,
    val link: (MonobankLink, Long?) -> Unit,
    val adjust: (MonobankLink) -> Unit,
    val addOperation: (TransactionType, Long, BigDecimal) -> Unit,
    val disconnect: () -> Unit,
    val forms: ((SourceForms) -> SourceForms) -> Unit,
    val preview: (FileSource) -> Unit,
    val fileProblem: (FileProblem) -> Unit,
)

@Composable
fun ImportsRoute(actions: ImportsActions, viewModel: ImportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tooBig = stringResource(R.string.feature_imports_file_too_big)
    val unreadable = stringResource(R.string.feature_imports_file_unreadable)
    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }
    ImportsScreen(
        state = state,
        snackbar = snackbar,
        back = actions.back,
        callbacks = ImportsCallbacks(
            refresh = viewModel::refresh,
            openConnect = viewModel::openConnect,
            closeConnect = viewModel::closeConnect,
            toggleManual = viewModel::toggleManual,
            connect = { token, since, schedule -> viewModel.connect(token, since, schedule, openUrl) },
            openAcceptUrl = openUrl,
            editSchedule = viewModel::editSchedule,
            saveSchedule = { viewModel.saveSchedule(showError) },
            syncNow = { viewModel.syncNow(showError) },
            link = { link, accountId -> viewModel.link(link, accountId, showError) },
            adjust = { viewModel.adjust(it, showError) },
            addOperation = actions.addOperation,
            disconnect = { viewModel.disconnect(showError) },
            forms = viewModel::updateForms,
            preview = { viewModel.preview(it, actions.openPreview) },
            fileProblem = { problem ->
                scope.launch { snackbar.showSnackbar(if (problem == FileProblem.TOO_BIG) tooBig else unreadable) }
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportsScreen(
    state: ImportsUiState,
    snackbar: SnackbarHostState,
    back: () -> Unit,
    callbacks: ImportsCallbacks,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_imports_title)) },
                navigationIcon = { IconButton(onClick = back) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when {
                state.loading -> SkeletonList(rows = 3)
                state.error != null && state.connection == null -> ErrorState(state.error, onRetry = callbacks.refresh)
                else -> MonobankCard(state, callbacks)
            }
            PrivatCard(state, callbacks)
            ZenCard(state, callbacks)
            FreedomCard(state, callbacks)
        }
    }
    if (state.connect != ConnectStep.Closed) ConnectSheet(state, callbacks)
}

// --- Monobank

@Composable
private fun MonobankCard(state: ImportsUiState, callbacks: ImportsCallbacks) {
    val connection = state.connection
    var confirmDisconnect by remember { mutableStateOf(false) }
    WwCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Monobank", style = MaterialTheme.typography.titleMedium)
            if (connection == null) {
                Text(
                    stringResource(R.string.feature_imports_mono_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = callbacks.openConnect) { Text(stringResource(R.string.feature_imports_mono_connect)) }
            } else {
                Text(
                    stringResource(
                        R.string.feature_imports_mono_connected_at,
                        connection.clientName,
                        dateTime(connection.connectedAt),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                connection.lastError?.let { WarnCard(it) }
                ScheduleCard(state, connection, callbacks)
                HorizontalDivider()
                Text(stringResource(R.string.feature_imports_mono_cards), style = MaterialTheme.typography.titleSmall)
                connection.links.forEach { link ->
                    LinkRow(
                        link,
                        state.accounts,
                        state.adjusting == link.id,
                        callbacks,
                    )
                }
                HorizontalDivider()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = callbacks.openConnect) {
                        Text(stringResource(R.string.feature_imports_mono_reconnect))
                    }
                    TextButton(onClick = { confirmDisconnect = true }, enabled = !state.disconnecting) {
                        Text(
                            stringResource(R.string.feature_imports_mono_disconnect),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
    if (confirmDisconnect) {
        ConfirmDialog(
            title = stringResource(R.string.feature_imports_mono_disconnect_title),
            body = stringResource(R.string.feature_imports_mono_disconnect_text),
            confirmText = stringResource(R.string.feature_imports_mono_disconnect),
            destructive = true,
            onConfirm = {
                confirmDisconnect = false
                callbacks.disconnect()
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

@Composable
private fun ScheduleCard(state: ImportsUiState, connection: MonobankConnection, callbacks: ImportsCallbacks) {
    val now = rememberNow()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.feature_imports_mono_schedule), style = MaterialTheme.typography.titleSmall)
        ScheduleFields(state.schedule, callbacks.editSchedule)
        if (state.scheduleDraft != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { callbacks.editSchedule(connection.schedule) }) {
                    Text(stringResource(R.string.feature_imports_cancel))
                }
                Button(onClick = callbacks.saveSchedule, enabled = !state.savingSchedule) {
                    Text(stringResource(R.string.feature_imports_mono_save_schedule))
                }
            }
        }
        val nextSync = connection.nextSyncAt
        val inProgress = nextSync != null && !nextSync.toInstant().isAfter(now)
        if (nextSync != null && !inProgress) {
            Text(
                stringResource(R.string.feature_imports_mono_next_sync, dateTime(nextSync)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val wait = connection.nextRequestAt?.let { Duration.between(now, it.toInstant()).seconds + 1 } ?: 0
        val waitSeconds = if (inProgress) 0 else wait.coerceAtLeast(0)
        OutlinedButton(onClick = callbacks.syncNow, enabled = !inProgress && waitSeconds <= 0) {
            Icon(Lucide.RefreshCw, contentDescription = null)
            Text(
                when {
                    inProgress -> stringResource(R.string.feature_imports_mono_syncing)
                    waitSeconds > 0 -> stringResource(R.string.feature_imports_mono_sync_in, waitSeconds.toInt())
                    else -> stringResource(R.string.feature_imports_mono_sync_now)
                },
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

/** The mode and its times; the bank's push mode is shown only for a connection that still has it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleFields(schedule: MonobankSchedule, onChange: (MonobankSchedule) -> Unit) {
    val modes = listOfNotNull(
        SyncMode.INTERVAL,
        SyncMode.DAILY,
        SyncMode.MANUAL,
        SyncMode.WEBHOOK.takeIf { schedule.syncMode == SyncMode.WEBHOOK },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = schedule.syncMode == mode,
                    onClick = { onChange(schedule.copy(syncMode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                ) { Text(modeName(mode), maxLines = 1) }
            }
        }
        when (schedule.syncMode) {
            SyncMode.INTERVAL -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TimeButton({ stringResource(R.string.feature_imports_mono_from, it) }, schedule.windowStart) {
                        onChange(schedule.copy(windowStart = it))
                    }
                    TimeButton({ stringResource(R.string.feature_imports_mono_to, it) }, schedule.windowEnd) {
                        onChange(schedule.copy(windowEnd = it))
                    }
                }
                Hint(stringResource(R.string.feature_imports_mono_interval_hint))
            }
            SyncMode.DAILY -> TimeButton(
                { stringResource(R.string.feature_imports_mono_daily_at, it) },
                schedule.dailyAt,
            ) { onChange(schedule.copy(dailyAt = it)) }
            SyncMode.MANUAL -> Hint(stringResource(R.string.feature_imports_mono_manual_hint))
            SyncMode.WEBHOOK -> Unit
        }
    }
}

@Composable
private fun modeName(mode: SyncMode): String = stringResource(
    when (mode) {
        SyncMode.INTERVAL -> R.string.feature_imports_mono_mode_interval
        SyncMode.DAILY -> R.string.feature_imports_mono_mode_daily
        SyncMode.MANUAL -> R.string.feature_imports_mono_mode_manual
        SyncMode.WEBHOOK -> R.string.feature_imports_mono_mode_webhook
    },
)

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LinkRow(link: MonobankLink, accounts: List<Account>, adjusting: Boolean, callbacks: ImportsCallbacks) {
    val money = formatters.money
    var picking by remember { mutableStateOf(false) }
    val notSynced = stringResource(R.string.feature_imports_mono_not_synced)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("${link.title} · ${link.currency.code}", style = MaterialTheme.typography.bodyLarge)
        ChoiceField(
            label = stringResource(R.string.feature_imports_mono_account_for, link.title),
            value = link.accountName ?: notSynced,
            onClick = { picking = true },
        )
        if (link.accountId != null) {
            val ours = link.balance?.let { money.format(it, link.currency.code) } ?: "—"
            val bank = link.bankBalance?.let { money.format(it, link.currency.code) } ?: "—"
            Hint(
                stringResource(R.string.feature_imports_mono_ours, ours) + " · " +
                    stringResource(R.string.feature_imports_mono_bank, bank),
            )
            val difference = link.difference ?: BigDecimal.ZERO
            if (difference.signum() != 0) {
                DifferenceActions(link, difference, adjusting, callbacks)
            } else {
                Hint(
                    link.syncedUntil?.let { stringResource(R.string.feature_imports_mono_synced_until, dateTime(it)) }
                        ?: stringResource(R.string.feature_imports_mono_loading),
                )
            }
        }
    }
    if (picking) {
        val options = listOf(PickerOption<Long?>(null, notSynced)) +
            accounts.filter { it.currency == link.currency }.map { PickerOption<Long?>(it.id, it.name) }
        PickerSheet(
            title = stringResource(R.string.feature_imports_mono_account_for, link.title),
            options = options,
            selected = link.accountId,
            onPick = { callbacks.link(link, it) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun DifferenceActions(
    link: MonobankLink,
    difference: BigDecimal,
    adjusting: Boolean,
    callbacks: ImportsCallbacks,
) {
    val accountId = link.accountId ?: return
    val income = difference.signum() > 0
    Text(
        formatters.money.format(difference, link.currency.code, signed = true) + " · " + stringResource(
            if (income) {
                R.string.feature_imports_mono_missing_income
            } else {
                R.string.feature_imports_mono_missing_expenses
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = WwTheme.colors.warnText,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = {
            callbacks.addOperation(
                if (income) TransactionType.INCOME else TransactionType.EXPENSE,
                accountId,
                difference.abs(),
            )
        }) {
            Text(
                stringResource(
                    if (income) R.string.feature_imports_mono_add_income else R.string.feature_imports_mono_add_expense,
                ),
            )
        }
        TextButton(onClick = { callbacks.adjust(link) }, enabled = !adjusting) {
            Text(stringResource(R.string.feature_imports_mono_adjust))
        }
    }
    Hint(stringResource(R.string.feature_imports_mono_adjust_hint))
}

// --- connect sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectSheet(state: ImportsUiState, callbacks: ImportsCallbacks) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = callbacks.closeConnect, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.feature_imports_mono_connect_title),
                style = MaterialTheme.typography.titleMedium,
            )
            when (val step = state.connect) {
                is ConnectStep.Form -> ConnectForm(state, step.manual, callbacks)
                is ConnectStep.Waiting -> {
                    Text(stringResource(R.string.feature_imports_mono_approve))
                    OutlinedButton(onClick = { callbacks.openAcceptUrl(step.auth.acceptUrl) }) {
                        Text(stringResource(R.string.feature_imports_mono_open_app))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = Spacing.sm))
                        Hint(stringResource(R.string.feature_imports_mono_waiting))
                    }
                    TextButton(
                        onClick = callbacks.closeConnect,
                    ) { Text(stringResource(R.string.feature_imports_cancel)) }
                }
                ConnectStep.Expired -> {
                    WarnCard(stringResource(R.string.feature_imports_mono_expired))
                    Button(
                        onClick = callbacks.openConnect,
                    ) { Text(stringResource(R.string.feature_imports_mono_retry)) }
                }
                ConnectStep.Closed -> Unit
            }
        }
    }
}

@Composable
private fun ConnectForm(state: ImportsUiState, manual: Boolean, callbacks: ImportsCallbacks) {
    val context = LocalContext.current
    val today = remember { LocalDate.now(java.time.ZoneId.of("Europe/Kyiv")) }
    val earliest = today.minusYears(HISTORY_YEARS).plusDays(1)
    var token by rememberSaveable { mutableStateOf("") }
    var since by rememberSaveable { mutableStateOf(today.minusYears(1).toString()) }
    var schedule by remember { mutableStateOf(state.connection?.schedule ?: MonobankSchedule()) }
    var pickDate by remember { mutableStateOf(false) }
    val viaApp = state.providerAvailable && !manual
    val fieldErrors = (state.connectError as? ApiError.Problem)?.fieldErrors.orEmpty()
    if (viaApp) {
        Text(stringResource(R.string.feature_imports_mono_provider_hint))
    } else {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.feature_imports_mono_token)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = fieldErrors["token"] != null,
            supportingText = { Text(fieldErrors["token"] ?: stringResource(R.string.feature_imports_mono_token_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MONOBANK_SITE))) }
        }) { Text(stringResource(R.string.feature_imports_mono_open_site)) }
    }
    ChoiceField(
        label = stringResource(R.string.feature_imports_mono_since),
        value = formatters.date.long(LocalDate.parse(since)),
        onClick = { pickDate = true },
        error = fieldErrors["since"],
    )
    Hint(stringResource(R.string.feature_imports_mono_since_hint))
    Text(stringResource(R.string.feature_imports_mono_schedule), style = MaterialTheme.typography.titleSmall)
    ScheduleFields(schedule) { schedule = it }
    Hint(stringResource(R.string.feature_imports_mono_background))
    state.connectError?.takeIf { fieldErrors.isEmpty() }?.let {
        Text(
            it.message(),
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (state.providerAvailable) {
        TextButton(onClick = callbacks.toggleManual) {
            Text(
                stringResource(
                    if (manual) R.string.feature_imports_mono_use_app else R.string.feature_imports_mono_use_token,
                ),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TextButton(onClick = callbacks.closeConnect) { Text(stringResource(R.string.feature_imports_cancel)) }
        Button(
            onClick = { callbacks.connect(token, LocalDate.parse(since), schedule) },
            enabled = !state.connecting && (viaApp || token.isNotBlank()),
        ) {
            Text(
                stringResource(
                    if (viaApp) {
                        R.string.feature_imports_mono_provider_connect
                    } else {
                        R.string.feature_imports_mono_connect
                    },
                ),
            )
        }
    }
    if (pickDate) {
        DatePickerDialogOf(
            initial = LocalDate.parse(since),
            onPick = {
                since = it.coerceIn(earliest, today).toString()
                pickDate = false
            },
            onDismiss = { pickDate = false },
        )
    }
}

// --- file sources

@Composable
private fun SourceCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    WwCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Hint(subtitle)
            content()
        }
    }
}

@Composable
private fun SourceError(state: ImportsUiState, source: FileSource) {
    val error = state.sources.error?.takeIf { it.first == source }?.second ?: return
    Text(error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SubmitButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
        if (busy) CircularProgressIndicator(Modifier.padding(end = Spacing.sm))
        Text(text)
    }
}

@Composable
private fun AccountChoice(
    label: String,
    auto: String,
    accounts: List<Account>,
    selected: Long?,
    onPick: (Long?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ChoiceField(label, accounts.firstOrNull { it.id == selected }?.name ?: auto, onClick = { open = true })
    if (open) {
        PickerSheet(
            title = label,
            options = listOf(PickerOption<Long?>(null, auto)) + accounts.map { PickerOption<Long?>(it.id, it.name) },
            selected = selected,
            onPick = onPick,
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun PrivatCard(state: ImportsUiState, callbacks: ImportsCallbacks) {
    val forms = state.sources
    SourceCard(
        stringResource(R.string.feature_imports_privat_title),
        stringResource(R.string.feature_imports_privat_subtitle),
    ) {
        FileField(
            label = stringResource(R.string.feature_imports_privat_file),
            file = forms.privatFile,
            mimeTypes = PDF,
            onFile = { file -> callbacks.forms { it.copy(privatFile = file) } },
            onProblem = callbacks.fileProblem,
        )
        Hint(stringResource(R.string.feature_imports_not_stored))
        AccountChoice(
            label = stringResource(R.string.feature_imports_privat_account),
            auto = stringResource(R.string.feature_imports_auto_account),
            accounts = state.accounts.filter { it.type == AccountType.CARD || it.type == AccountType.CREDIT_CARD },
            selected = forms.privatAccountId,
            onPick = { id -> callbacks.forms { it.copy(privatAccountId = id) } },
        )
        Hint(stringResource(R.string.feature_imports_privat_account_hint))
        SourceError(state, FileSource.PRIVATBANK)
        SubmitButton(
            stringResource(R.string.feature_imports_preview_submit),
            enabled = forms.privatFile != null,
            busy = forms.busy == FileSource.PRIVATBANK,
        ) { callbacks.preview(FileSource.PRIVATBANK) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZenCard(state: ImportsUiState, callbacks: ImportsCallbacks) {
    val forms = state.sources
    SourceCard(
        stringResource(R.string.feature_imports_zen_title),
        stringResource(R.string.feature_imports_zen_subtitle),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ZenMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = forms.zenMode == mode,
                    onClick = { callbacks.forms { it.copy(zenMode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index, ZenMode.entries.size),
                ) {
                    Text(
                        stringResource(
                            if (mode == ZenMode.TOKEN) {
                                R.string.feature_imports_zen_token
                            } else {
                                R.string.feature_imports_zen_file
                            },
                        ),
                    )
                }
            }
        }
        if (forms.zenMode == ZenMode.TOKEN) {
            OutlinedTextField(
                value = forms.zenToken,
                onValueChange = { value -> callbacks.forms { it.copy(zenToken = value) } },
                label = { Text(stringResource(R.string.feature_imports_zen_token_label)) },
                supportingText = { Text(stringResource(R.string.feature_imports_zen_token_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FileField(
                label = stringResource(R.string.feature_imports_zen_file_label),
                file = forms.zenFile,
                mimeTypes = CSV,
                onFile = { file -> callbacks.forms { it.copy(zenFile = file) } },
                onProblem = callbacks.fileProblem,
            )
            Hint(stringResource(R.string.feature_imports_zen_file_hint))
        }
        SourceError(state, FileSource.ZENMONEY)
        SubmitButton(
            stringResource(R.string.feature_imports_zen_submit),
            enabled = if (forms.zenMode == ZenMode.TOKEN) forms.zenToken.isNotBlank() else forms.zenFile != null,
            busy = forms.busy == FileSource.ZENMONEY,
        ) { callbacks.preview(FileSource.ZENMONEY) }
    }
}

@Composable
private fun FreedomCard(state: ImportsUiState, callbacks: ImportsCallbacks) {
    val forms = state.sources
    SourceCard(
        stringResource(R.string.feature_imports_freedom_title),
        stringResource(R.string.feature_imports_freedom_subtitle),
    ) {
        FileField(
            label = stringResource(R.string.feature_imports_freedom_trades),
            file = forms.freedomTrades,
            mimeTypes = XLSX,
            onFile = { file: UploadFile? -> callbacks.forms { it.copy(freedomTrades = file) } },
            onProblem = callbacks.fileProblem,
        )
        FileField(
            label = stringResource(R.string.feature_imports_freedom_cash),
            file = forms.freedomCash,
            mimeTypes = XLSX,
            onFile = { file: UploadFile? -> callbacks.forms { it.copy(freedomCash = file) } },
            onProblem = callbacks.fileProblem,
        )
        Hint(stringResource(R.string.feature_imports_freedom_files_hint))
        AccountChoice(
            label = stringResource(R.string.feature_imports_freedom_account),
            auto = stringResource(R.string.feature_imports_freedom_auto),
            accounts = state.accounts.filter { it.type == AccountType.INVESTMENT },
            selected = forms.freedomAccountId,
            onPick = { id -> callbacks.forms { it.copy(freedomAccountId = id) } },
        )
        Hint(stringResource(R.string.feature_imports_freedom_account_hint))
        SourceError(state, FileSource.FREEDOM)
        SubmitButton(
            stringResource(R.string.feature_imports_preview_submit),
            enabled = forms.freedomTrades != null || forms.freedomCash != null,
            busy = forms.busy == FileSource.FREEDOM,
        ) { callbacks.preview(FileSource.FREEDOM) }
    }
}
