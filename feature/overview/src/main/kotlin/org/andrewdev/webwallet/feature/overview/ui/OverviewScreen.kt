@file:Suppress("MatchingDeclarationName")

package org.andrewdev.webwallet.feature.overview.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.data.BankDifference
import org.andrewdev.webwallet.core.data.MonthFigures
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.AccountSummary
import org.andrewdev.webwallet.core.model.BalanceMode
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionSource
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.CategoryIcon
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.Figure
import org.andrewdev.webwallet.core.ui.IconTile
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.StaleBanner
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.feature.overview.OverviewUiState
import org.andrewdev.webwallet.feature.overview.OverviewViewModel
import org.andrewdev.webwallet.feature.overview.R
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth

class OverviewActions(
    val openTransactions: () -> Unit,
    val openTransaction: (Long) -> Unit,
    val openBudget: () -> Unit,
    val openAccounts: () -> Unit,
    val newAccount: () -> Unit,
    /** The transactions of a category in the month, from the top categories. */
    val openCategory: (id: Long, name: String, month: java.time.YearMonth) -> Unit = { _, _, _ -> },
)

@Composable
fun OverviewRoute(actions: OverviewActions, viewModel: OverviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OverviewScreen(
        state,
        actions,
        onRefresh = viewModel::refresh,
        onBalanceMode = viewModel::setBalanceMode,
        onForecastMinimum = viewModel::setForecastMinimum,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    state: OverviewUiState,
    actions: OverviewActions,
    onRefresh: () -> Unit,
    onBalanceMode: (BalanceMode) -> Unit,
    onForecastMinimum: (java.math.BigDecimal?) -> Unit = {},
) {
    val f = formatters
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.feature_overview_title))
                        Text(
                            f.date.month(YearMonth.from(f.today)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                OverviewUiState.Loading -> SkeletonList(rows = 4)
                is OverviewUiState.Error -> ErrorState(state.error, onRetry = onRefresh)
                is OverviewUiState.Content -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                    OverviewContent(state, actions, onRefresh, onBalanceMode, onForecastMinimum)
                }
            }
        }
    }
}

@Composable
private fun OverviewContent(
    state: OverviewUiState.Content,
    actions: OverviewActions,
    onRefresh: () -> Unit,
    onBalanceMode: (BalanceMode) -> Unit,
    onForecastMinimum: (java.math.BigDecimal?) -> Unit = {},
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.stale) item { StaleBanner(state.fetchedAt, state.offline, onRefresh) }
        if (!state.hasAccounts) {
            item {
                WwCard {
                    EmptyState(
                        title = stringResource(R.string.feature_overview_empty_title),
                        body = stringResource(R.string.feature_overview_empty_text),
                        action = stringResource(R.string.feature_overview_empty_action),
                        onAction = actions.newAccount,
                    )
                }
            }
        }
        state.summary?.let { summary -> item { Hero(summary, state.balanceMode, onBalanceMode) } }
        item { MonthCard(state.month, state.summary, actions.openBudget) }
        state.updateAvailable?.let { config ->
            item {
                WarnCard(
                    text = stringResource(R.string.feature_overview_update_available),
                    action = stringResource(R.string.feature_overview_update_action),
                    onAction = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(config.storeUrl)),
                            )
                        }
                    },
                )
            }
        }
        if (state.differences.isNotEmpty()) {
            item {
                WarnCard(
                    text = stringResource(
                        R.string.feature_overview_monobank_difference,
                        state.differences.joinToString { it.accountName },
                    ),
                    action = stringResource(R.string.feature_overview_to_accounts),
                    onAction = actions.openAccounts,
                )
            }
        }
        if (state.overdueCount > 0) {
            item {
                WarnCard(
                    text = pluralStringResource(
                        R.plurals.feature_overview_overdue,
                        state.overdueCount,
                        state.overdueCount,
                    ),
                    action = stringResource(R.string.feature_overview_to_transactions),
                    onAction = actions.openTransactions,
                )
            }
        }
        state.forecast?.let { forecast ->
            item { ForecastCard(forecast, state.forecastMinimum, onForecastMinimum, actions.openTransactions) }
        }
        state.spending?.let { spending ->
            item { TopCategoriesCard(spending, state.categoriesById, state.currentMonth, actions.openCategory) }
        }
        state.sixMonths?.let { (_, months) -> item { SixMonthsCard(months) } }
        state.budget?.let { month -> item { EnvelopesCard(month, state.monthProgress, actions.openBudget) } }
        item { RecentCard(state, actions) }
    }
}

@Composable
private fun Hero(summary: AccountSummary, mode: BalanceMode, onMode: (BalanceMode) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val value = when (mode) {
        BalanceMode.NET_WORTH -> summary.netWorth
        BalanceMode.AVAILABLE_WITH_CREDIT -> summary.availableWithCredit
        BalanceMode.READY_TO_ASSIGN -> summary.readyToAssign
    }
    WwCard {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(modifier = Modifier.clickable { menu = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    balanceModeName(mode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Lucide.ChevronDown,
                    contentDescription = stringResource(R.string.feature_overview_balance_mode),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    BalanceMode.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(balanceModeName(candidate))
                                    Text(
                                        balanceModeHint(candidate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                onMode(candidate)
                                menu = false
                            },
                        )
                    }
                }
            }
            Amount(value, summary.currency.code, style = WwTheme.type.amountL, tone = AmountTone.BySign)
            if (summary.unconvertedCurrencies.isNotEmpty()) {
                Text(
                    stringResource(R.string.feature_overview_unconverted, summary.unconvertedCurrencies.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun balanceModeName(mode: BalanceMode): String = stringResource(
    when (mode) {
        BalanceMode.NET_WORTH -> R.string.feature_overview_net_worth
        BalanceMode.AVAILABLE_WITH_CREDIT -> R.string.feature_overview_available_with_credit
        BalanceMode.READY_TO_ASSIGN -> R.string.feature_overview_ready_to_assign
    },
)

@Composable
private fun balanceModeHint(mode: BalanceMode): String = stringResource(
    when (mode) {
        BalanceMode.NET_WORTH -> R.string.feature_overview_net_worth_hint
        BalanceMode.AVAILABLE_WITH_CREDIT -> R.string.feature_overview_available_with_credit_hint
        BalanceMode.READY_TO_ASSIGN -> R.string.feature_overview_ready_to_assign_hint
    },
)

@Composable
private fun MonthCard(month: MonthFigures?, summary: AccountSummary?, onBudget: () -> Unit) {
    val currency = month?.currency?.code ?: summary?.currency?.code ?: "UAH"
    val f = formatters
    WwCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Figure(label = stringResource(R.string.feature_overview_income), modifier = Modifier.weight(1f)) {
                Amount(month?.income ?: BigDecimal.ZERO, currency, color = WwTheme.colors.income)
            }
            Figure(
                label = stringResource(R.string.feature_overview_expense),
                modifier = Modifier.weight(1f),
                note = month?.savingsRate?.let {
                    stringResource(
                        R.string.feature_overview_savings_rate,
                        f.money.formatPercent(it, 0),
                    )
                },
            ) {
                Amount(month?.expense ?: BigDecimal.ZERO, currency, color = WwTheme.colors.expense)
            }
        }
        summary?.let {
            Figure(
                label = stringResource(R.string.feature_overview_ready_to_assign),
                onClick = onBudget,
                note = stringResource(R.string.feature_overview_to_budget),
            ) {
                Amount(it.readyToAssign, it.currency.code, tone = AmountTone.BySign)
            }
        }
    }
}

@Composable
private fun RecentCard(state: OverviewUiState.Content, actions: OverviewActions) {
    WwCard {
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.feature_overview_recent),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = actions.openTransactions,
                ) { Text(stringResource(R.string.feature_overview_all_transactions)) }
            }
            if (state.recent.isEmpty()) {
                Text(
                    stringResource(R.string.feature_overview_no_transactions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            state.recent.forEach { transaction ->
                RecentRow(transaction, state, onClick = {
                    actions.openTransaction(transaction.id)
                })
            }
        }
    }
}

@Composable
private fun RecentRow(transaction: Transaction, state: OverviewUiState.Content, onClick: () -> Unit) {
    val f = formatters
    val category = (
        transaction.categoryId ?: transaction.splits.firstOrNull {
            it.categoryId != null
        }?.categoryId
        )?.let(state.categoriesById::get)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (transaction.isTransfer) {
            IconTile(
                Lucide.ArrowLeftRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                container = MaterialTheme.colorScheme.surfaceVariant,
                size = 36.dp,
            )
        } else {
            CategoryIcon(category, size = 36.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when {
                    transaction.isTransfer -> {
                        val from = state.accountsById[transaction.accountId]?.name.orEmpty()
                        val to = state.accountsById[transaction.toAccountId]?.name.orEmpty()
                        "$from → $to"
                    }
                    else ->
                        transaction.payee?.name ?: category?.name
                            ?: stringResource(R.string.feature_overview_no_payee)
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    f.date.short(
                        transaction.date,
                        f.today,
                    ),
                    state.accountsById[transaction.accountId]?.name?.takeIf {
                        !transaction.isTransfer
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Amount(
            transaction.signedAmount,
            transaction.currency.code,
            tone = AmountTone.ByType(transaction.type),
            signed = !transaction.isTransfer,
        )
    }
}

@PreviewLightDark
@Composable
@Suppress("MagicNumber")
private fun OverviewPreview() {
    val transaction = Transaction(
        id = 1, type = TransactionType.EXPENSE,
        date = LocalDate.of(
            2026,
            10,
            25,
        ),
        accountId = 1, amount = BigDecimal("640.50"),
        currency = CurrencyCode.UAH, toAccountId = null, toAmount = null, toCurrency = null, categoryId = null,
        payee = org.andrewdev.webwallet.core.model.Named(
            1,
            "Сільпо",
        ),
        comment = null, tagIds = emptyList(), splits = emptyList(),
        originalAmount = null, originalCurrency = null, source = TransactionSource.MONOBANK,
        adjustment = false, recurring = null,
        createdAt = OffsetDateTime.parse("2026-10-25T10:00:00Z"),
    )
    WebWalletTheme {
        OverviewScreen(
            OverviewUiState.Content(
                summary = AccountSummary(
                    CurrencyCode.UAH,
                    BigDecimal("125400.20"),
                    BigDecimal("40200"),
                    BigDecimal("60200"),
                    BigDecimal("3500"),
                    listOf("GEL"),
                ),
                balanceMode = BalanceMode.NET_WORTH,
                month = MonthFigures(
                    CurrencyCode.UAH,
                    BigDecimal("52000"),
                    BigDecimal("31240.10"),
                    BigDecimal("0.4"),
                    emptyList(),
                ),
                recent = listOf(transaction),
                accountsById = emptyMap(),
                categoriesById = emptyMap(),
                differences = listOf(BankDifference(1, "Чорна картка", BigDecimal("-120"), CurrencyCode.UAH)),
                overdueCount = 2,
                updateAvailable = null,
                hasAccounts = true,
                forecast = null,
                forecastMinimum = null,
                spending = null,
                sixMonths = null,
                budget = null,
                monthProgress = 0.6f,
                currentMonth = java.time.YearMonth.of(2026, 10),
                stale = false,
                fetchedAt = null,
                offline = false,
                refreshing = false,
            ),
            OverviewActions({}, {}, {}, {}, {}),
            {},
            {},
        )
    }
}
