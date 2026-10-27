package org.andrewdev.webwallet.feature.settings.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.model.NotificationSettings
import org.andrewdev.webwallet.core.model.User
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.settings.NotificationsUiState
import org.andrewdev.webwallet.feature.settings.NotificationsViewModel
import org.andrewdev.webwallet.feature.settings.R

@Composable
fun NotificationsRoute(onBack: () -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    val context = LocalContext.current
    NotificationsScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSave = { transform -> viewModel.save(transform, showError) },
        onLink = { password, code, onDone ->
            viewModel.link(password, code) { result ->
                when (result) {
                    is ApiResult.Ok -> {
                        onDone(null)
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.value))) }
                    }
                    is ApiResult.Err -> onDone(result.error)
                }
            }
        },
        onUnlink = { viewModel.unlink(showError) },
        onQuickEntryAccount = { viewModel.setQuickEntryAccount(it, showError) },
    )
}

/** The notifications (docs/04 §14, docs/06 §1.4): the Telegram card, the hour, a switch per kind, email digests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NotificationsScreen(
    state: NotificationsUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: ((NotificationSettings) -> NotificationSettings) -> Unit,
    onLink: (password: String?, code: String?, onDone: (ApiError?) -> Unit) -> Unit,
    onUnlink: () -> Unit,
    onQuickEntryAccount: (Long?) -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    var pickHour by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    val f = formatters
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_notifications)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val settings = state.settings
        when {
            state.loading && settings == null -> SkeletonList(rows = 6, modifier = Modifier.padding(padding))
            state.error != null && settings == null -> ErrorState(state.error, onRetry = onRefresh)
            settings != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val telegram = state.telegram
                WwCard {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text("Telegram", style = MaterialTheme.typography.titleSmall)
                        when {
                            telegram == null -> Unit
                            !telegram.enabled -> Text(
                                stringResource(R.string.feature_settings_telegram_disabled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            telegram.linked -> {
                                Text(
                                    stringResource(
                                        R.string.feature_settings_telegram_linked_as,
                                        listOfNotNull(telegram.firstName, telegram.username?.let { "@$it" })
                                            .joinToString(" "),
                                        telegram.linkedAt?.let { f.date.long(it.toLocalDate()) }.orEmpty(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.feature_settings_telegram_account),
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            state.accounts.firstOrNull { it.id == telegram.quickEntryAccountId }?.name
                                                ?: stringResource(R.string.feature_settings_telegram_account_default),
                                        )
                                    },
                                    modifier = Modifier.clickable { pickAccount = true },
                                )
                                Text(
                                    stringResource(R.string.feature_settings_telegram_account_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = onUnlink) {
                                    Text(stringResource(R.string.feature_settings_telegram_unlink))
                                }
                            }
                            state.linkPending -> {
                                Text(
                                    stringResource(
                                        R.string.feature_settings_telegram_pending,
                                        telegram.botUsername?.let { "@$it" }.orEmpty(),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            else -> {
                                Text(
                                    stringResource(R.string.feature_settings_telegram_text),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = { confirming = true }) {
                                    Text(stringResource(R.string.feature_settings_telegram_link))
                                }
                            }
                        }
                    }
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.feature_settings_notifications_hour)) },
                    supportingContent = { Text(String.format(f.language.locale, "%02d:00", settings.hour)) },
                    modifier = Modifier.clickable { pickHour = true },
                )
                if (telegram?.linked != true) {
                    WarnCard(text = stringResource(R.string.feature_settings_notifications_not_linked))
                }
                Text(
                    stringResource(R.string.feature_settings_notifications_reminders),
                    style = MaterialTheme.typography.titleSmall,
                )
                SwitchRow(
                    title = stringResource(R.string.feature_settings_notifications_recurring),
                    hint = stringResource(R.string.feature_settings_notifications_recurring_text),
                    checked = settings.recurring,
                ) { on -> onSave { it.copy(recurring = on) } }
                SwitchRow(
                    title = stringResource(R.string.feature_settings_notifications_budgets),
                    hint = stringResource(R.string.feature_settings_notifications_budgets_text),
                    checked = settings.budgets,
                ) { on -> onSave { it.copy(budgets = on) } }
                SwitchRow(
                    title = stringResource(R.string.feature_settings_notifications_taxes),
                    hint = stringResource(R.string.feature_settings_notifications_taxes_text),
                    checked = settings.taxes,
                ) { on -> onSave { it.copy(taxes = on) } }
                Text(
                    stringResource(R.string.feature_settings_notifications_events),
                    style = MaterialTheme.typography.titleSmall,
                )
                SwitchRow(
                    title = stringResource(R.string.feature_settings_notifications_banks),
                    hint = stringResource(R.string.feature_settings_notifications_banks_text),
                    checked = settings.banks,
                ) { on -> onSave { it.copy(banks = on) } }
                SwitchRow(
                    title = stringResource(R.string.feature_settings_notifications_forecast),
                    hint = stringResource(R.string.feature_settings_notifications_forecast_text),
                    checked = settings.forecast,
                ) { on -> onSave { it.copy(forecast = on) } }
                SwitchRow(
                    title = stringResource(R.string.feature_settings_notifications_goals),
                    hint = stringResource(R.string.feature_settings_notifications_goals_text),
                    checked = settings.goals,
                ) { on -> onSave { it.copy(goals = on) } }
                Text(
                    stringResource(R.string.feature_settings_notifications_reports),
                    style = MaterialTheme.typography.titleSmall,
                )
                SwitchRow(
                    title = stringResource(R.string.feature_settings_notifications_weekly),
                    hint = stringResource(R.string.feature_settings_notifications_weekly_text),
                    checked = settings.weekly,
                ) { on -> onSave { it.copy(weekly = on) } }
                SwitchRow(
                    title = stringResource(
                        R.string.feature_settings_notifications_email,
                        stringResource(R.string.feature_settings_notifications_weekly),
                    ),
                    hint = null,
                    checked = settings.emailWeekly,
                ) { on -> onSave { it.copy(emailWeekly = on) } }
                SwitchRow(
                    title = stringResource(R.string.feature_settings_notifications_monthly),
                    hint = stringResource(R.string.feature_settings_notifications_monthly_text),
                    checked = settings.monthly,
                ) { on -> onSave { it.copy(monthly = on) } }
                SwitchRow(
                    title = stringResource(
                        R.string.feature_settings_notifications_email,
                        stringResource(R.string.feature_settings_notifications_monthly),
                    ),
                    hint = state.user?.email?.let {
                        stringResource(
                            R.string.feature_settings_notifications_email_hint,
                            it,
                        )
                    },
                    checked = settings.emailMonthly,
                ) { on -> onSave { it.copy(emailMonthly = on) } }
            }
        }
    }
    if (confirming) {
        ConfirmIdentitySheet(
            user = state.user,
            onSubmit = { password, code, onDone ->
                onLink(password, code) { error ->
                    onDone(error)
                    if (error == null) confirming = false
                }
            },
            onDismiss = { confirming = false },
        )
    }
    if (pickHour) {
        ModalBottomSheet(onDismissRequest = { pickHour = false }) {
            Column(modifier = Modifier.padding(bottom = Spacing.xxl).verticalScroll(rememberScrollState())) {
                for (hour in 0 until HOURS) {
                    ListItem(
                        headlineContent = { Text(String.format(f.language.locale, "%02d:00", hour)) },
                        modifier = Modifier.clickable {
                            onSave { it.copy(hour = hour) }
                            pickHour = false
                        },
                    )
                }
            }
        }
    }
    if (pickAccount) {
        ModalBottomSheet(onDismissRequest = { pickAccount = false }) {
            Column(modifier = Modifier.padding(bottom = Spacing.xxl)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.feature_settings_telegram_account_default)) },
                    modifier = Modifier.clickable {
                        onQuickEntryAccount(null)
                        pickAccount = false
                    },
                )
                state.accounts.forEach { account ->
                    ListItem(
                        headlineContent = { Text(account.name) },
                        supportingContent = { Text(account.currency.code) },
                        modifier = Modifier.clickable {
                            onQuickEntryAccount(account.id)
                            pickAccount = false
                        },
                    )
                }
            }
        }
    }
}

/** The password or a code of the authenticator before a change of how the user signs in or is reached. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfirmIdentitySheet(
    user: User?,
    onSubmit: (password: String?, code: String?, onDone: (ApiError?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val byCode = user?.twoFactorEnabled == true || user?.hasPassword == false
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.feature_settings_confirm_identity),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    if (byCode) {
                        R.string.feature_settings_confirm_code_hint
                    } else {
                        R.string.feature_settings_confirm_password_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
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
            error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_cancel)) }
                Button(
                    onClick = {
                        busy = true
                        onSubmit(value.takeIf { !byCode }, value.takeIf { byCode }) {
                            busy = false
                            error = it
                        }
                    },
                    enabled = value.isNotBlank() && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.feature_settings_continue)) }
            }
        }
    }
}

private const val HOURS = 24
