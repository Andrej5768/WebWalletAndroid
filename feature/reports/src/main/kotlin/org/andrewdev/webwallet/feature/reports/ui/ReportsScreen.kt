package org.andrewdev.webwallet.feature.reports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.data.CacheState
import org.andrewdev.webwallet.core.designsystem.theme.PaletteColor
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.CategorySpending
import org.andrewdev.webwallet.core.model.IncomeExpenseReport
import org.andrewdev.webwallet.core.model.MonthNetWorth
import org.andrewdev.webwallet.core.model.NetWorthReport
import org.andrewdev.webwallet.core.model.PayeeSpending
import org.andrewdev.webwallet.core.model.PayeesReport
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.PeriodPreset
import org.andrewdev.webwallet.core.model.SpendingReport
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.CategoryIcon
import org.andrewdev.webwallet.core.ui.ChartColumn
import org.andrewdev.webwallet.core.ui.ChartLine
import org.andrewdev.webwallet.core.ui.ColumnChart
import org.andrewdev.webwallet.core.ui.DatePickerDialogOf
import org.andrewdev.webwallet.core.ui.DonutChart
import org.andrewdev.webwallet.core.ui.DonutSlice
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.Figure
import org.andrewdev.webwallet.core.ui.LineChart
import org.andrewdev.webwallet.core.ui.ProgressBar
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.StaleBanner
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.feature.reports.R
import org.andrewdev.webwallet.feature.reports.ReportTab
import org.andrewdev.webwallet.feature.reports.ReportsActions
import org.andrewdev.webwallet.feature.reports.ReportsUiState
import org.andrewdev.webwallet.feature.reports.ReportsViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

@Composable
fun ReportsRoute(actions: ReportsActions, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val analytics by viewModel.analyticsState.collectAsStateWithLifecycle()
    ReportsScreen(
        state = state,
        actions = actions,
        onTab = viewModel::setTab,
        onPreset = viewModel::setPreset,
        onRefresh = viewModel::refresh,
        analytics = { AnalyticsTabs(analytics, viewModel) },
    )
}

/** The reports (docs/04 §9): the tabs, the period chip row and the report of the tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun ReportsScreen(
    state: ReportsUiState,
    actions: ReportsActions,
    onTab: (ReportTab) -> Unit,
    onPreset: (PeriodPreset, Period) -> Unit,
    onRefresh: () -> Unit,
    analytics: @Composable () -> Unit,
) {
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    val f = formatters
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_reports_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.back) { Icon(Lucide.ArrowLeft, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = state.tab.ordinal) {
                ReportTab.entries.forEach { tab ->
                    Tab(selected = state.tab == tab, onClick = { onTab(tab) }, text = { Text(tabName(tab)) })
                }
            }
            if (state.tab != ReportTab.ANALYTICS) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(PRESETS) { preset ->
                        FilterChip(
                            selected = state.preset == preset,
                            onClick = {
                                if (preset == PeriodPreset.CUSTOM) {
                                    pickFrom = true
                                } else {
                                    onPreset(
                                        preset,
                                        state.custom,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    if (preset == PeriodPreset.CUSTOM && state.preset == PeriodPreset.CUSTOM) {
                                        "${state.period.from?.let { f.date.short(it, f.today) }} – " +
                                            state.period.to?.let { f.date.short(it, f.today) }
                                    } else {
                                        presetName(preset)
                                    },
                                )
                            },
                        )
                    }
                }
            }
            when (state.tab) {
                ReportTab.SPENDING -> Report(state.spending, onRefresh) { report ->
                    SpendingReportView(report, state, actions)
                }
                ReportTab.INCOME -> Report(state.incomeExpense, onRefresh) { report -> IncomeExpenseView(report) }
                ReportTab.NET_WORTH -> Report(state.netWorth, onRefresh) { report -> NetWorthView(report) }
                ReportTab.PAYEES -> Report(state.payees, onRefresh) { report -> PayeesView(report, actions) }
                ReportTab.ANALYTICS -> analytics()
            }
        }
    }
    if (pickFrom) {
        DatePickerDialogOf(state.period.from ?: f.today.withDayOfMonth(1), onPick = { from ->
            onPreset(PeriodPreset.CUSTOM, Period(from, state.custom.to ?: f.today))
            pickFrom = false
            pickTo = true
        }, onDismiss = { pickFrom = false })
    }
    if (pickTo) {
        DatePickerDialogOf(state.custom.to ?: f.today, onPick = { to ->
            onPreset(PeriodPreset.CUSTOM, Period(state.custom.from, to))
            pickTo = false
        }, onDismiss = { pickTo = false })
    }
}

/** The loading, error and stale handling every report shares. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Any> Report(state: CacheState<T>, onRefresh: () -> Unit, content: @Composable (T) -> Unit) {
    val data = state.dataOrNull
    val missingError = (state as? CacheState.Missing)?.takeIf { !it.loading }?.error
    when {
        data != null -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state is CacheState.Stale) {
                    StaleBanner(
                        state.fetchedAt,
                        state.error is org.andrewdev.webwallet.core.network.ApiError.Network,
                        onRefresh,
                    )
                }
                content(data)
            }
        }
        missingError != null -> ErrorState(missingError, onRetry = onRefresh)
        else -> SkeletonList(rows = 6)
    }
}

@Composable
internal fun Unconverted(currencies: List<String>) {
    if (currencies.isNotEmpty()) {
        WarnCard(text = stringResource(R.string.feature_reports_unconverted, currencies.joinToString(", ")))
    }
}

// --- spending

@Composable
private fun SpendingReportView(report: SpendingReport, state: ReportsUiState, actions: ReportsActions) {
    var selected by remember { mutableStateOf<Int?>(null) }
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val dark = isSystemInDarkTheme()
    val currency = report.currency.code
    val f = formatters
    val top = report.categories.take(TOP_SLICES)
    val other = report.categories.drop(TOP_SLICES)
    val otherAmount = other.fold(BigDecimal.ZERO) { sum, c -> sum + c.amount } + report.uncategorized
    val colorOf = { c: CategorySpending ->
        PaletteColor.of(
            state.categoriesById[c.categoryId]?.color,
            c.categoryId,
        ).color(dark)
    }
    val month = YearMonth.from(report.from)
    if (report.categories.isEmpty() && report.uncategorized.signum() == 0) {
        EmptyState(title = stringResource(R.string.feature_reports_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Unconverted(report.unconvertedCurrencies) }
        item {
            WwCard {
                Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        slices = top.map { DonutSlice(it.amount.toFloat(), colorOf(it)) } +
                            listOfNotNull(
                                DonutSlice(otherAmount.toFloat(), WwTheme.colors.faint)
                                    .takeIf { otherAmount.signum() > 0 },
                            ),
                        selected = selected,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.feature_reports_total),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Amount(report.total, currency, compact = true)
                        }
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        top.forEachIndexed { index, c ->
                            LegendRow(c.name, colorOf(c), f.money.formatPercent(c.share, 0), selected == index) {
                                selected = index.takeIf { selected != index }
                            }
                        }
                        if (otherAmount.signum() > 0) {
                            LegendRow(
                                stringResource(R.string.feature_reports_other),
                                WwTheme.colors.faint,
                                f.money.formatPercent(
                                    otherAmount.divide(
                                        report.total.max(BigDecimal.ONE),
                                        SHARE_SCALE,
                                        RoundingMode.HALF_UP,
                                    ),
                                    0,
                                ),
                                selected == top.size,
                            ) { selected = top.size.takeIf { selected != top.size } }
                        }
                    }
                }
            }
        }
        report.categories.forEach { category ->
            item(key = category.categoryId) {
                SpendingRow(
                    row = category,
                    category = state.categoriesById[category.categoryId],
                    currency = currency,
                    expandable = category.children.isNotEmpty(),
                    expanded = category.categoryId in expanded,
                    onToggle = {
                        val id = category.categoryId
                        expanded = if (id in expanded) expanded - id else expanded + id
                    },
                    onOpen = { actions.openCategory(category.categoryId, category.name, month) },
                )
            }
            if (category.categoryId in expanded) {
                items(category.children, key = { "child-${it.categoryId}" }) { child ->
                    SpendingRow(
                        row = child,
                        category = state.categoriesById[child.categoryId],
                        currency = currency,
                        expandable = false,
                        expanded = false,
                        indent = true,
                        onToggle = {},
                        onOpen = { actions.openCategory(child.categoryId, child.name, month) },
                    )
                }
            }
        }
        if (report.uncategorized.signum() > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.feature_reports_uncategorized), modifier = Modifier.weight(1f))
                    Amount(report.uncategorized, currency)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(name: String, color: Color, share: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(end = Spacing.xs).size(DOT).background(color, CircleShape))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(share, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@Suppress("LongParameterList")
private fun SpendingRow(
    row: CategorySpending,
    category: Category?,
    currency: String,
    expandable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    indent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) Spacing.xl else 0.dp)
            .clickable(onClick = onOpen)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category, size = if (indent) 28.dp else 36.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Amount(row.amount, currency)
            }
            ProgressBar(share = row.share.toFloat(), modifier = Modifier.padding(top = Spacing.xs), height = 4.dp)
        }
        if (expandable) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
                    contentDescription = stringResource(
                        if (expanded) R.string.feature_reports_collapse else R.string.feature_reports_expand,
                        row.name,
                    ),
                )
            }
        }
    }
}

// --- income and expenses

@Composable
private fun IncomeExpenseView(report: IncomeExpenseReport) {
    val f = formatters
    val currency = report.currency.code
    if (report.months.isEmpty()) {
        EmptyState(title = stringResource(R.string.feature_reports_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Unconverted(report.unconvertedCurrencies) }
        item {
            WwCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure(label = stringResource(R.string.feature_reports_income), modifier = Modifier.weight(1f)) {
                        Amount(report.income, currency, color = WwTheme.colors.income)
                    }
                    Figure(label = stringResource(R.string.feature_reports_expense), modifier = Modifier.weight(1f)) {
                        Amount(report.expense, currency, color = WwTheme.colors.expense)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure(label = stringResource(R.string.feature_reports_net), modifier = Modifier.weight(1f)) {
                        Amount(report.net, currency, tone = AmountTone.BySign, signed = true)
                    }
                    Figure(
                        label = stringResource(R.string.feature_reports_savings_rate),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            report.savingsRate?.let { f.money.formatPercent(it, 0) } ?: "—",
                            style = WwTheme.type.amountM,
                        )
                    }
                }
            }
        }
        item {
            WwCard {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    ColumnChart(
                        columns = report.months.map {
                            ChartColumn(
                                listOf(it.income.toFloat(), it.expense.toFloat()),
                                it.month,
                            )
                        },
                        colors = listOf(WwTheme.colors.income, WwTheme.colors.expense),
                    )
                    MonthLabels(report.months.map { it.month })
                }
            }
        }
        items(report.months.reversed(), key = { it.month }) { month ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    f.date.month(YearMonth.parse(month.month)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Amount(month.net, currency, tone = AmountTone.BySign, signed = true)
                    Text(
                        "${f.money.format(month.income, currency)} · ${f.money.format(month.expense, currency)}" +
                            (month.savingsRate?.let { " · " + f.money.formatPercent(it, 0) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The month labels under a chart, the first letters only when there are many. */
@Composable
internal fun MonthLabels(months: List<String>) {
    val f = formatters
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        months.forEach { month ->
            Text(
                f.date.month(YearMonth.parse(month)).take(if (months.size > MANY_MONTHS) 1 else MONTH_LABEL),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- net worth

@Composable
private fun NetWorthView(report: NetWorthReport) {
    val f = formatters
    val currency = report.currency.code
    if (report.months.isEmpty()) {
        EmptyState(title = stringResource(R.string.feature_reports_empty))
        return
    }
    val first = report.months.first()
    val last = report.months.last()

    @Composable
    fun change(pick: (MonthNetWorth) -> BigDecimal) =
        stringResource(
            R.string.feature_reports_change,
            f.money.format(pick(last) - pick(first), currency, signed = true),
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Unconverted(report.unconvertedCurrencies) }
        item {
            WwCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure(
                        label = stringResource(R.string.feature_reports_assets),
                        note = change {
                            it.assets
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Amount(last.assets, currency)
                    }
                    Figure(
                        label = stringResource(R.string.feature_reports_debts),
                        note = change {
                            it.liabilities
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Amount(last.liabilities, currency)
                    }
                }
                Figure(label = stringResource(R.string.feature_reports_net_worth), note = change { it.netWorth }) {
                    Amount(last.netWorth, currency, style = WwTheme.type.amountL, tone = AmountTone.BySign)
                }
            }
        }
        item {
            WwCard {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    LineChart(
                        lines = listOf(
                            ChartLine(report.months.map { it.assets.toFloat() }, WwTheme.colors.income),
                            ChartLine(report.months.map { it.liabilities.toFloat() }, WwTheme.colors.expense),
                            ChartLine(
                                report.months.map { it.netWorth.toFloat() },
                                MaterialTheme.colorScheme.primary,
                                filled = true,
                            ),
                        ),
                        include = listOf(0f),
                    )
                    MonthLabels(report.months.map { it.month })
                }
            }
        }
    }
}

// --- payees

@Composable
private fun PayeesView(report: PayeesReport, actions: ReportsActions) {
    val currency = report.currency.code
    if (report.payees.isEmpty()) {
        EmptyState(title = stringResource(R.string.feature_reports_empty))
        return
    }
    val max = report.payees.maxOf { it.amount }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.lg)) {
        item { Unconverted(report.unconvertedCurrencies) }
        itemsIndexed(report.payees) { index, payee ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    actions.openPayee(payee.payeeId, payee.name)
                }.padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(RANK),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text(
                            payee.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Amount(payee.amount, currency)
                    }
                    Text(
                        pluralStringResource(
                            R.plurals.feature_reports_operations,
                            payee.transactions,
                            payee.transactions,
                        ) + " · " + formatters.money.format(payee.average(), currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProgressBar(
                        share = if (max.signum() > 0) {
                            payee.amount.divide(
                                max,
                                SHARE_SCALE,
                                RoundingMode.HALF_UP,
                            ).toFloat()
                        } else {
                            0f
                        },
                        modifier = Modifier.padding(top = Spacing.xs),
                        height = 4.dp,
                    )
                }
            }
        }
    }
}

/** The spending per transaction of a payee. */
private fun PayeeSpending.average(): BigDecimal =
    amount.divide(BigDecimal(transactions.coerceAtLeast(1)), 2, RoundingMode.HALF_UP)

@Composable
fun tabName(tab: ReportTab): String = stringResource(
    when (tab) {
        ReportTab.SPENDING -> R.string.feature_reports_tab_spending
        ReportTab.INCOME -> R.string.feature_reports_tab_income
        ReportTab.NET_WORTH -> R.string.feature_reports_tab_net_worth
        ReportTab.PAYEES -> R.string.feature_reports_tab_payees
        ReportTab.ANALYTICS -> R.string.feature_reports_tab_analytics
    },
)

@Composable
fun presetName(preset: PeriodPreset): String = stringResource(
    when (preset) {
        PeriodPreset.MONTH -> R.string.feature_reports_preset_month
        PeriodPreset.PREV_MONTH -> R.string.feature_reports_preset_prev_month
        PeriodPreset.QUARTER -> R.string.feature_reports_preset_quarter
        PeriodPreset.YEAR -> R.string.feature_reports_preset_year
        PeriodPreset.TWELVE_MONTHS -> R.string.feature_reports_preset_twelve_months
        PeriodPreset.ALL -> R.string.feature_reports_preset_all
        PeriodPreset.CUSTOM -> R.string.feature_reports_preset_custom
    },
)

private val PRESETS = listOf(
    PeriodPreset.MONTH,
    PeriodPreset.PREV_MONTH,
    PeriodPreset.QUARTER,
    PeriodPreset.YEAR,
    PeriodPreset.TWELVE_MONTHS,
    PeriodPreset.ALL,
    PeriodPreset.CUSTOM,
)
private const val TOP_SLICES = 8
private const val SHARE_SCALE = 4
private const val MANY_MONTHS = 8
private const val MONTH_LABEL = 3
private val DOT = 10.dp
private val RANK = 28.dp
