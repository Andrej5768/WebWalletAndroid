package org.andrewdev.webwallet.feature.imports.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.BrokerOperation
import org.andrewdev.webwallet.core.model.BrokerOperationStatus
import org.andrewdev.webwallet.core.model.BrokerOperationType
import org.andrewdev.webwallet.core.model.BrokerPreview
import org.andrewdev.webwallet.core.model.BrokerTicker
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.imports.BrokerFilter
import org.andrewdev.webwallet.feature.imports.BrokerModel
import org.andrewdev.webwallet.feature.imports.BrokerPreviewViewModel
import org.andrewdev.webwallet.feature.imports.BrokerUiState
import org.andrewdev.webwallet.feature.imports.R
import org.andrewdev.webwallet.feature.imports.TickerChoice
import java.math.BigDecimal
import java.math.MathContext

private val SMALL = BigDecimal("0.01")
private const val SMALL_DECIMALS = 4
private const val RATE_DIGITS = 6

@Composable
fun BrokerPreviewRoute(id: String, onClose: () -> Unit, viewModel: BrokerPreviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    val leave = {
        viewModel.discard()
        onClose()
    }
    BackHandler(enabled = !state.confirming, onBack = leave)
    val result = state.result
    if (result != null) {
        ResultScreen(
            title = stringResource(R.string.feature_imports_result_title),
            stats = listOf(
                stringResource(R.string.feature_imports_broker_result_operations) to result.operationsImported,
                stringResource(R.string.feature_imports_broker_result_duplicates) to result.duplicates,
                stringResource(R.string.feature_imports_broker_result_instruments) to result.instrumentsCreated,
                stringResource(R.string.feature_imports_broker_result_accounts) to if (result.accountCreated) 1 else 0,
            ),
            warnings = emptyList(),
            linkText = null,
            onLink = {},
            onClose = onClose,
        )
        return
    }
    BrokerPreviewScreen(state, leave, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrokerPreviewScreen(state: BrokerUiState, onBack: () -> Unit, viewModel: BrokerPreviewViewModel) {
    val preview = state.preview
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_imports_preview_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
            )
        },
        bottomBar = {
            if (preview != null && !state.expired) {
                Surface(tonalElevation = Spacing.xs) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
                        state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                        Button(
                            onClick = viewModel::confirm,
                            enabled = state.importing > 0 && !state.confirming,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.confirming) CircularProgressIndicator(Modifier.padding(end = Spacing.sm))
                            Text(
                                pluralStringResource(
                                    R.plurals.feature_imports_submit,
                                    state.importing,
                                    state.importing,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (preview == null || state.expired) {
            EmptyState(
                title = stringResource(R.string.feature_imports_preview_expired),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val visible = preview.operations.filter { BrokerModel.matches(it, state.filter) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { BrokerHeader(preview, state) }
            if (preview.instruments.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.feature_imports_broker_instruments),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(preview.instruments, key = { "ticker:${it.ticker}" }) { ticker ->
                    TickerCard(ticker, preview, state, viewModel)
                }
            }
            item { BrokerFilters(state, viewModel) }
            items(visible, key = { it.key }) { operation ->
                OperationRow(operation, operation.key !in state.excluded) { viewModel.include(operation.key, it) }
            }
            item { AfterImport(preview, state) }
        }
    }
}

@Composable
private fun BrokerHeader(preview: BrokerPreview, state: BrokerUiState) {
    val accountText = if (preview.accountId == null) {
        stringResource(R.string.feature_imports_broker_new_account, preview.accountName)
    } else {
        stringResource(R.string.feature_imports_broker_account, preview.accountName)
    }
    val period = if (preview.from != null && preview.to != null) {
        formatters.date.long(preview.from!!) + " – " + formatters.date.long(preview.to!!)
    } else {
        null
    }
    val duplicates = preview.operations.count { it.status == BrokerOperationStatus.DUPLICATE }
    val manual = preview.instruments.count { BrokerModel.isManual(it, state.choices[it.ticker]) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(listOfNotNull("Freedom Finance", accountText, period).joinToString(" · "))
        Text(
            stringResource(R.string.feature_imports_broker_summary, state.importing, duplicates, manual),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preview.providerUnavailable) WarnCard(stringResource(R.string.feature_imports_broker_provider_unavailable))
    }
}

@Composable
private fun TickerCard(
    ticker: BrokerTicker,
    preview: BrokerPreview,
    state: BrokerUiState,
    viewModel: BrokerPreviewViewModel,
) {
    val choice = state.choices[ticker.ticker] ?: TickerChoice.SUGGESTED
    val suggestion = ticker.suggestion
    val manual = BrokerModel.isManual(ticker, choice)
    val quantity = BrokerModel.quantityAfter(ticker, preview.operations, state.excluded)
    val createManual = stringResource(R.string.feature_imports_broker_create_manual, suggestion.symbol, ticker.currency)
    val suggestedLabel = if (suggestion.manual) {
        createManual
    } else {
        listOfNotNull(suggestion.symbol, suggestion.name, suggestion.exchange, suggestion.currency).joinToString(" · ")
    }
    var picking by remember { mutableStateOf(false) }
    WwCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ticker.ticker, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val (text, color) = when {
                    ticker.shortfall -> stringResource(R.string.feature_imports_broker_state_shortfall) to
                        MaterialTheme.colorScheme.error
                    manual -> stringResource(R.string.feature_imports_broker_state_manual) to WwTheme.colors.warnText
                    choice == TickerChoice.SUGGESTED && suggestion.instrumentId == null ->
                        stringResource(R.string.feature_imports_broker_state_found) to WwTheme.colors.income
                    else -> stringResource(R.string.feature_imports_broker_state_known) to WwTheme.colors.income
                }
                Text(text, style = MaterialTheme.typography.labelMedium, color = color)
            }
            ChoiceField(
                label = stringResource(R.string.feature_imports_broker_instruments),
                value = if (choice == TickerChoice.MANUAL) createManual else suggestedLabel,
                onClick = { if (!suggestion.manual) picking = true },
            )
            Text(
                if (quantity.signum() > 0) {
                    stringResource(R.string.feature_imports_broker_units, formatters.money.formatQuantity(quantity))
                } else {
                    stringResource(R.string.feature_imports_broker_closed)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (picking) {
        PickerSheet(
            title = ticker.ticker,
            options = listOf(
                PickerOption(TickerChoice.SUGGESTED, suggestedLabel),
                PickerOption(TickerChoice.MANUAL, createManual),
            ),
            selected = choice,
            onPick = { viewModel.choose(ticker.ticker, it) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun BrokerFilters(state: BrokerUiState, viewModel: BrokerPreviewViewModel) {
    val fresh = state.preview?.operations.orEmpty().count { it.status == BrokerOperationStatus.NEW }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.feature_imports_broker_rows), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            BrokerFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filterName(filter)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = fresh > 0 && state.excluded.isEmpty(),
                onCheckedChange = { viewModel.includeAll(it) },
            )
            Text(stringResource(R.string.feature_imports_select_all))
        }
    }
}

@Composable
private fun filterName(filter: BrokerFilter): String = stringResource(
    when (filter) {
        BrokerFilter.ALL -> R.string.feature_imports_filter_all
        BrokerFilter.TRADES -> R.string.feature_imports_broker_filter_trades
        BrokerFilter.INCOME -> R.string.feature_imports_broker_filter_income
        BrokerFilter.FEES -> R.string.feature_imports_broker_filter_fees
        BrokerFilter.CASH -> R.string.feature_imports_broker_filter_cash
        BrokerFilter.SKIPPED -> R.string.feature_imports_broker_filter_skipped
    },
)

@Composable
private fun typeName(type: BrokerOperationType): String = stringResource(
    when (type) {
        BrokerOperationType.BUY -> R.string.feature_imports_broker_type_buy
        BrokerOperationType.SELL -> R.string.feature_imports_broker_type_sell
        BrokerOperationType.DIVIDEND -> R.string.feature_imports_broker_type_dividend
        BrokerOperationType.COUPON -> R.string.feature_imports_broker_type_coupon
        BrokerOperationType.FEE -> R.string.feature_imports_broker_type_fee
        BrokerOperationType.SPLIT -> R.string.feature_imports_broker_type_split
        BrokerOperationType.DEPOSIT -> R.string.feature_imports_broker_type_deposit
        BrokerOperationType.WITHDRAWAL -> R.string.feature_imports_broker_type_withdrawal
        BrokerOperationType.EXCHANGE -> R.string.feature_imports_broker_type_exchange
    },
)

/** One row of the reports: what it is, the units and the change of cash, and whether it is imported. */
@Composable
@Suppress("CyclomaticComplexMethod")
private fun OperationRow(operation: BrokerOperation, included: Boolean, onInclude: (Boolean) -> Unit) {
    val money = formatters.money
    val format = { amount: BigDecimal, currency: String, signed: Boolean ->
        val decimals = if (amount.signum() != 0 && amount.abs() < SMALL) SMALL_DECIMALS else 2
        money.format(amount, currency, signed, decimals)
    }
    val fresh = operation.status == BrokerOperationStatus.NEW
    val type = operation.type
    val title = when (type) {
        BrokerOperationType.BUY, BrokerOperationType.SELL, BrokerOperationType.DIVIDEND, BrokerOperationType.COUPON ->
            operation.ticker.orEmpty()
        BrokerOperationType.EXCHANGE -> "${operation.currency} → ${operation.toCurrency}"
        null -> stringResource(
            if (operation.skip == "SERVICE") {
                R.string.feature_imports_broker_skip_service
            } else {
                R.string.feature_imports_broker_skip_unknown
            },
        )
        else -> typeName(type)
    }
    val detail = when (type) {
        BrokerOperationType.BUY, BrokerOperationType.SELL ->
            money.formatQuantity(operation.quantity ?: BigDecimal.ZERO) + " × " +
                format(operation.price ?: BigDecimal.ZERO, operation.currency, false)
        BrokerOperationType.EXCHANGE -> {
            val toAmount = operation.toAmount
            if (toAmount != null && operation.amount.signum() != 0) {
                toAmount.divide(operation.amount, MathContext(RATE_DIGITS)).toPlainString()
            } else {
                ""
            }
        }
        else -> operation.description
    }
    val cash = if (type == BrokerOperationType.EXCHANGE && operation.toAmount != null && operation.toCurrency != null) {
        format(operation.toAmount!!, operation.toCurrency!!, true)
    } else if (operation.currency.isNotEmpty()) {
        format(operation.cash, operation.currency, true)
    } else {
        ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = fresh && included, onCheckedChange = onInclude, enabled = fresh)
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(type?.let { typeName(it) }?.takeIf { it != title }, title).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(formatters.date.dayMonth(operation.date), detail).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!fresh) {
                Text(
                    stringResource(
                        if (operation.status == BrokerOperationStatus.DUPLICATE) {
                            R.string.feature_imports_broker_status_duplicate
                        } else {
                            R.string.feature_imports_broker_status_skipped
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(cash, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AfterImport(preview: BrokerPreview, state: BrokerUiState) {
    val money = formatters.money
    val held = preview.instruments
        .map { it to BrokerModel.quantityAfter(it, preview.operations, state.excluded) }
        .filter { (_, quantity) -> quantity.signum() > 0 }
    val positions = if (held.isEmpty()) {
        stringResource(R.string.feature_imports_broker_no_positions)
    } else {
        held.joinToString(", ") { (ticker, quantity) -> "${ticker.ticker} — ${money.formatQuantity(quantity)}" }
    }
    val cash = BrokerModel.cashAfter(preview.cash, preview.operations, state.excluded)
        .joinToString(", ") { money.format(it.amount, it.currency.code) }
    val shortfalls = preview.instruments.filter { it.shortfall }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.md)) {
        Text(stringResource(R.string.feature_imports_broker_after, positions, cash))
        Text(
            stringResource(R.string.feature_imports_broker_after_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (shortfalls.isNotEmpty()) {
            WarnCard(
                stringResource(R.string.feature_imports_broker_shortfall, shortfalls.joinToString(", ") { it.ticker }),
            )
        }
    }
}
