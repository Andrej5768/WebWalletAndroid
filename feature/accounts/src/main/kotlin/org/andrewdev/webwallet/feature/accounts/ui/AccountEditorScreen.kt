package org.andrewdev.webwallet.feature.accounts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.ui.AccountTypeIcon
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.MoneyField
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.accounts.AccountEditorUiState
import org.andrewdev.webwallet.feature.accounts.AccountEditorViewModel
import org.andrewdev.webwallet.feature.accounts.R
import java.math.BigDecimal
import java.util.Currency

/** UAH and the currencies people here keep money in first, then the whole ISO list (docs/04 §5.2). */
private val COMMON_CURRENCIES =
    listOf(
        "UAH", "USD", "EUR", "GBP", "PLN", "CZK", "CHF", "CAD", "TRY", "GEL", "KZT",
        "JPY", "CNY", "SEK", "NOK", "DKK", "HUF", "RON", "BGN", "ILS", "AUD",
    )

fun allCurrencies(): List<String> = COMMON_CURRENCIES + Currency.getAvailableCurrencies()
    .map { it.currencyCode }
    .filter { it !in COMMON_CURRENCIES && it.all { c -> c in 'A'..'Z' } }
    .sorted()

@Composable
fun AccountEditorRoute(id: Long?, onClose: () -> Unit, viewModel: AccountEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    AccountEditorScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onTypeChange = viewModel::onTypeChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onOpeningBalanceChange = viewModel::onOpeningBalanceChange,
        onCreditLimitChange = viewModel::onCreditLimitChange,
        onInterestRateChange = viewModel::onInterestRateChange,
        onInBudgetChange = viewModel::onInBudgetChange,
        onArchivedChange = viewModel::onArchivedChange,
        onSave = { viewModel.save(onClose) },
        onDelete = { viewModel.delete(onClose) },
        onClose = onClose,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun AccountEditorScreen(
    state: AccountEditorUiState,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningBalanceChange: (String, BigDecimal?) -> Unit,
    onCreditLimitChange: (String, BigDecimal?) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onInBudgetChange: (Boolean) -> Unit,
    onArchivedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    val close = { if (state.dirty) confirmDiscard = true else onClose() }
    val nameRequired = stringResource(R.string.feature_accounts_form_name_required)
    BackHandler(onBack = close)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.feature_accounts_form_new
                            } else {
                                R.string.feature_accounts_form_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Lucide.X, contentDescription = stringResource(R.string.feature_accounts_form_close))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Lucide.Trash2,
                                contentDescription = stringResource(R.string.feature_accounts_form_delete),
                            )
                        }
                    }
                    TextButton(onClick = onSave, enabled = !state.saving && state.loaded) {
                        Text(stringResource(R.string.feature_accounts_form_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.feature_accounts_form_name)) },
                isError = state.nameMissing || "name" in state.fieldErrors,
                supportingText = (state.fieldErrors["name"] ?: nameRequired.takeIf { state.nameMissing })
                    ?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.feature_accounts_form_type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                AccountType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(typeName(type)) },
                        leadingIcon = { AccountTypeIcon(type, size = 20.dp) },
                    )
                }
            }
            OutlinedTextField(
                value = state.currency,
                onValueChange = {},
                readOnly = true,
                enabled = state.isNew,
                label = { Text(stringResource(R.string.feature_accounts_form_currency)) },
                supportingText = if (!state.isNew) {
                    (
                        {
                            Text(
                                stringResource(R.string.feature_accounts_form_currency_locked),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        )
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = state.isNew) { pickCurrency = true },
            )
            MoneyField(
                text = state.openingBalanceText,
                onTextChange = { onOpeningBalanceChange(it, state.openingBalance) },
                onValueChange = { onOpeningBalanceChange(state.openingBalanceText, it) },
                currency = state.currency,
                label = stringResource(R.string.feature_accounts_form_opening_balance),
                allowNegative = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.type.hasCreditLimit) {
                MoneyField(
                    text = state.creditLimitText,
                    onTextChange = { onCreditLimitChange(it, state.creditLimit) },
                    onValueChange = { onCreditLimitChange(state.creditLimitText, it) },
                    currency = state.currency,
                    label = stringResource(R.string.feature_accounts_form_credit_limit),
                    error = null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.feature_accounts_form_credit_limit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.type.hasInterest) {
                OutlinedTextField(
                    value = state.interestRateText,
                    onValueChange = onInterestRateChange,
                    label = { Text(stringResource(R.string.feature_accounts_form_interest_rate)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SwitchRow(
                label = stringResource(R.string.feature_accounts_form_in_budget),
                hint = stringResource(R.string.feature_accounts_form_in_budget_hint),
                checked = state.inBudget,
                onChange = onInBudgetChange,
            )
            if (!state.isNew) {
                SwitchRow(
                    label = stringResource(R.string.feature_accounts_form_archived),
                    hint = null,
                    checked = state.archived,
                    onChange = onArchivedChange,
                )
            }
            state.error?.let {
                Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.deleteBlocked) {
                Text(
                    stringResource(R.string.feature_accounts_form_delete_blocked),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (pickCurrency) {
        CurrencySheet(onPick = {
            onCurrencyChange(it)
            pickCurrency = false
        }, onDismiss = { pickCurrency = false })
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.feature_accounts_form_discard_title),
            confirmText = stringResource(R.string.feature_accounts_form_discard),
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.feature_accounts_form_delete_title, state.name),
            body = stringResource(R.string.feature_accounts_form_delete_text),
            confirmText = stringResource(R.string.feature_accounts_form_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember { allCurrencies() }
    val shown = all.filter { it.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.feature_accounts_form_search_currency)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(shown, key = { it }) { code ->
                ListItem(
                    headlineContent = { Text(code) },
                    supportingContent = {
                        Text(
                            runCatching { Currency.getInstance(code).displayName }.getOrDefault(""),
                        )
                    },
                    modifier = Modifier.clickable { onPick(code) },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AccountEditorPreview() {
    WebWalletTheme {
        AccountEditorScreen(
            AccountEditorUiState(
                loaded = true,
                name = "Кредитка",
                type = AccountType.CREDIT_CARD,
                creditLimitText = "20000",
            ),
            {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {},
        )
    }
}
