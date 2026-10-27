package org.andrewdev.webwallet.feature.reports.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import org.andrewdev.webwallet.core.designsystem.theme.PaletteColor
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.CompareReport
import org.andrewdev.webwallet.core.model.IncomeExpenseReport
import org.andrewdev.webwallet.core.model.NeedsWantsPart
import org.andrewdev.webwallet.core.model.NeedsWantsReport
import org.andrewdev.webwallet.core.model.SavingsReport
import org.andrewdev.webwallet.core.model.TrendsReport
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.ChartColumn
import org.andrewdev.webwallet.core.ui.ChartLine
import org.andrewdev.webwallet.core.ui.ChartMarker
import org.andrewdev.webwallet.core.ui.ColumnChart
import org.andrewdev.webwallet.core.ui.DatePickerDialogOf
import org.andrewdev.webwallet.core.ui.Figure
import org.andrewdev.webwallet.core.ui.LineChart
import org.andrewdev.webwallet.core.ui.ProgressBar
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.feature.reports.AnalyticsTab
import org.andrewdev.webwallet.feature.reports.AnalyticsUiState
import org.andrewdev.webwallet.feature.reports.ComparePeriods
import org.andrewdev.webwallet.feature.reports.ComparePreset
import org.andrewdev.webwallet.feature.reports.R
import org.andrewdev.webwallet.feature.reports.ReportsViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/** The analytics sub-tabs (docs/04 §9): cash flow, compare, trends, savings. Plan vs actual moves to the budget. */
@Composable
internal fun AnalyticsTabs(state: AnalyticsUiState, viewModel: ReportsViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryScrollableTabRow(selectedTabIndex = state.tab.ordinal) {
            AnalyticsTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { viewModel.setAnalyticsTab(tab) },
                    text = { Text(analyticsTabName(tab)) },
                )
            }
        }
        when (state.tab) {
            AnalyticsTab.CASH_FLOW -> Report(state.cashFlow, viewModel::refresh) { CashFlowView(it) }
            AnalyticsTab.COMPARE -> Report(state.compare, viewModel::refresh) { report ->
                CompareView(
                    report = report,
                    state = state,
                    onPreset = viewModel::setComparePreset,
                    onSameDate = viewModel::setSameDate,
                    onCustom = viewModel::setCustomPeriods,
                )
            }
            AnalyticsTab.TRENDS -> Report(state.trends, viewModel::refresh) { report ->
                TrendsView(report, state.trendSelection, viewModel::toggleTrend)
            }
            AnalyticsTab.SAVINGS -> Report(state.savings, viewModel::refresh) { report ->
                SavingsView(report, state.needsWants.dataOrNull, state.savingsTarget, viewModel::setSavingsTarget)
            }
        }
    }
}

@Composable
private fun analyticsTabName(tab: AnalyticsTab): String = stringResource(
    when (tab) {
        AnalyticsTab.CASH_FLOW -> R.string.feature_reports_analytics_cash_flow
        AnalyticsTab.COMPARE -> R.string.feature_reports_analytics_compare
        AnalyticsTab.TRENDS -> R.string.feature_reports_analytics_trends
        AnalyticsTab.SAVINGS -> R.string.feature_reports_analytics_savings
    },
)

// --- cash flow

@Composable
private fun CashFlowView(report: IncomeExpenseReport) {
    val f = formatters
    val currency = report.currency.code
    val average = if (report.months.isEmpty()) {
        BigDecimal.ZERO
    } else {
        report.net.divide(
            BigDecimal(report.months.size),
            2,
            RoundingMode.HALF_UP,
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Unconverted(report.unconvertedCurrencies) }
        item {
            WwCard {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.feature_reports_cash_flow_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.feature_reports_cash_flow_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ColumnChart(
                        columns = report.months.map {
                            ChartColumn(
                                listOf(it.income.toFloat(), it.expense.toFloat()),
                                it.month,
                            )
                        },
                        colors = listOf(WwTheme.colors.income, WwTheme.colors.expense),
                    )
                    LineChart(
                        lines = listOf(
                            ChartLine(report.months.map { it.net.toFloat() }, MaterialTheme.colorScheme.primary),
                        ),
                        markers = listOf(ChartMarker(0f, MaterialTheme.colorScheme.outline, dashed = false)),
                        height = SMALL_CHART,
                    )
                    MonthLabels(report.months.map { it.month })
                    Text(
                        if (average.signum() >= 0) {
                            stringResource(
                                R.string.feature_reports_cash_flow_average,
                                f.money.format(average, currency),
                            )
                        } else {
                            stringResource(
                                R.string.feature_reports_cash_flow_average_negative,
                                f.money.format(average.negate(), currency),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(report.months.reversed(), key = { it.month }) { month ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.date.month(YearMonth.parse(month.month)), modifier = Modifier.weight(1f))
                Amount(month.net, currency, tone = AmountTone.BySign, signed = true)
            }
        }
    }
}

// --- compare

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun CompareView(
    report: CompareReport,
    state: AnalyticsUiState,
    onPreset: (ComparePreset) -> Unit,
    onSameDate: (Boolean) -> Unit,
    onCustom: (ComparePeriods) -> Unit,
) {
    val f = formatters
    val currency = report.currency.code
    var picking by remember { mutableStateOf<Int?>(null) }
    val labelA = "${f.date.short(report.fromA, f.today)} – ${f.date.short(report.toA, f.today)}"
    val labelB = "${f.date.short(report.fromB, f.today)} – ${f.date.short(report.toB, f.today)}"
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ComparePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.comparePreset == preset,
                        onClick = { if (preset == ComparePreset.CUSTOM) picking = 0 else onPreset(preset) },
                        label = {
                            Text(
                                stringResource(
                                    when (preset) {
                                        ComparePreset.MOM -> R.string.feature_reports_compare_mom
                                        ComparePreset.YOY -> R.string.feature_reports_compare_yoy
                                        ComparePreset.CUSTOM -> R.string.feature_reports_compare_custom
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    onSameDate(!state.sameDate)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.feature_reports_compare_same_date), modifier = Modifier.weight(1f))
                Switch(checked = state.sameDate, onCheckedChange = onSameDate)
            }
        }
        item { Unconverted(report.unconvertedCurrencies) }
        item {
            WwCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure(
                        label = stringResource(R.string.feature_reports_compare_period_a),
                        note = labelA,
                        modifier = Modifier.weight(1f),
                    ) {
                        Amount(report.totalA, currency)
                    }
                    Figure(
                        label = stringResource(R.string.feature_reports_compare_period_b),
                        note = labelB,
                        modifier = Modifier.weight(1f),
                    ) {
                        Amount(report.totalB, currency)
                    }
                }
                Figure(
                    label = stringResource(R.string.feature_reports_compare_change),
                    note = report.changeShare?.let { f.money.formatPercent(it, 0) },
                ) {
                    Amount(
                        report.change,
                        currency,
                        signed = true,
                        color = if (report.change.signum() > 0) WwTheme.colors.expense else WwTheme.colors.income,
                    )
                }
            }
        }
        if (report.days.isNotEmpty()) {
            item {
                WwCard {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            stringResource(R.string.feature_reports_compare_cumulative),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.feature_reports_compare_cumulative_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val a = report.days.mapNotNull { it.a?.toFloat() }
                        val b = report.days.mapNotNull { it.b?.toFloat() }
                        LineChart(
                            lines = listOf(
                                ChartLine(a, MaterialTheme.colorScheme.primary),
                                ChartLine(b, MaterialTheme.colorScheme.onSurfaceVariant, dashed = true),
                            ),
                            points = report.days.size,
                            include = listOf(0f),
                        )
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.feature_reports_compare_biggest), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.feature_reports_compare_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(report.categories.sortedByDescending { it.change.abs() }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.name ?: stringResource(R.string.feature_reports_uncategorized),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${f.money.format(row.a, currency)} · ${f.money.format(row.b, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Amount(
                    row.change,
                    currency,
                    signed = true,
                    color = if (row.change.signum() > 0) WwTheme.colors.expense else WwTheme.colors.income,
                )
            }
        }
    }
    picking?.let { step ->
        val periods = state.periods ?: return@let
        val initial = listOf(periods.fromA, periods.toA, periods.fromB, periods.toB)
        DatePickerDialogOf(initial[step], onPick = { date ->
            val next = initial.toMutableList().also { it[step] = date }
            picking = if (step < initial.lastIndex) step + 1 else null
            onCustom(ComparePeriods(fromA = next[FROM_A], toA = next[TO_A], fromB = next[FROM_B], toB = next[TO_B]))
        }, onDismiss = { picking = null })
    }
}

// --- trends

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendsView(report: TrendsReport, selection: Set<Long?>, onToggle: (Long?) -> Unit) {
    val f = formatters
    val currency = report.currency.code
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val shown = report.categories.filter { it.categoryId in selection }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Unconverted(report.unconvertedCurrencies) }
        item {
            WwCard {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.feature_reports_trends_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            R.string.feature_reports_trends_range,
                            report.from,
                            report.to,
                            ReportsViewModel.MAX_TREND_LINES,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LineChart(
                        lines = shown.map { trend ->
                            ChartLine(
                                trend.amounts.map { it.toFloat() },
                                PaletteColor.of(null, trend.categoryId ?: 0).color(dark),
                            )
                        },
                        include = listOf(0f),
                        points = report.months.size,
                    )
                    MonthLabels(report.months)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        report.categories.forEach { trend ->
                            FilterChip(
                                selected = trend.categoryId in selection,
                                onClick = { onToggle(trend.categoryId) },
                                label = { Text(trend.name ?: stringResource(R.string.feature_reports_uncategorized)) },
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.feature_reports_trends_anomalies), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.feature_reports_trends_anomalies_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val anomalies = report.categories.flatMap { trend -> trend.anomalies.map { trend to it } }
        if (anomalies.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.feature_reports_trends_no_anomalies),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(anomalies) { (trend, anomaly) ->
            Text(
                stringResource(
                    R.string.feature_reports_trends_anomaly,
                    trend.name ?: stringResource(R.string.feature_reports_uncategorized),
                    f.date.month(YearMonth.parse(anomaly.month)),
                    f.money.format(anomaly.amount, currency),
                    f.money.formatQuantity(anomaly.ratio, 1),
                    f.money.format(anomaly.average, currency),
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = Spacing.xs),
            )
        }
    }
}

// --- savings

@Composable
private fun SavingsView(
    report: SavingsReport,
    needsWants: NeedsWantsReport?,
    target: BigDecimal,
    onTarget: (BigDecimal) -> Unit,
) {
    val f = formatters
    val currency = report.currency.code
    val last = report.months.lastOrNull { it.complete }
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
                        label = stringResource(R.string.feature_reports_savings_rate),
                        note = stringResource(
                            R.string.feature_reports_savings_rate_note,
                            f.money.formatPercent(target, 0),
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        val rate = report.averageRate
                        Text(
                            rate?.let { f.money.formatPercent(it, 0) } ?: "—",
                            style = WwTheme.type.amountM,
                            color = if (rate != null && rate < target) {
                                WwTheme.colors.expense
                            } else {
                                WwTheme.colors.income
                            },
                        )
                    }
                    Figure(
                        label = stringResource(R.string.feature_reports_savings_total),
                        note = pluralStringResource(
                            R.plurals.feature_reports_savings_months,
                            report.months.size,
                            report.months.size,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Amount(report.totalSaved, currency, tone = AmountTone.BySign) }
                }
                Column(modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.md)) {
                    Text(
                        stringResource(R.string.feature_reports_savings_target, f.money.formatPercent(target, 0)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = target.toFloat(),
                        onValueChange = { onTarget(BigDecimal(it.toDouble()).setScale(2, RoundingMode.HALF_UP)) },
                        valueRange = TARGET_MIN..TARGET_MAX,
                        steps = TARGET_STEPS,
                    )
                }
            }
        }
        item {
            WwCard {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.feature_reports_savings_chart),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.feature_reports_savings_chart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ColumnChart(
                        columns = report.months.map { ChartColumn(listOf(it.net.toFloat()), it.month) },
                        colors = listOf(MaterialTheme.colorScheme.primary),
                    )
                    LineChart(
                        lines = listOf(
                            ChartLine(
                                report.months.map { it.cumulative.toFloat() },
                                WwTheme.colors.income,
                                filled = true,
                            ),
                        ),
                        include = listOf(0f),
                        height = SMALL_CHART,
                    )
                    MonthLabels(report.months.map { it.month })
                }
            }
        }
        if (needsWants != null && last != null) {
            item {
                WwCard {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            stringResource(
                                R.string.feature_reports_savings_rule,
                                f.date.month(YearMonth.parse(last.month)),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.feature_reports_savings_rule_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (needsWants.income.signum() <= 0) {
                            Text(
                                stringResource(R.string.feature_reports_savings_no_income),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            PartRow(
                                stringResource(R.string.feature_reports_savings_needs),
                                needsWants.needs,
                                currency,
                                WwTheme.colors.expense,
                            )
                            PartRow(
                                stringResource(R.string.feature_reports_savings_wants),
                                needsWants.wants,
                                currency,
                                MaterialTheme.colorScheme.primary,
                            )
                            PartRow(
                                stringResource(R.string.feature_reports_savings_savings_part),
                                needsWants.savings,
                                currency,
                                WwTheme.colors.income,
                            )
                            if (needsWants.unclassified.amount.signum() > 0) {
                                PartRow(
                                    stringResource(R.string.feature_reports_savings_unclassified),
                                    needsWants.unclassified,
                                    currency,
                                    WwTheme.colors.faint,
                                )
                            }
                        }
                    }
                }
            }
            item {
                WwCard {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            stringResource(R.string.feature_reports_savings_destinations),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(
                                R.string.feature_reports_savings_destinations_month,
                                f.date.month(YearMonth.parse(last.month)),
                                f.money.format(last.net, currency, signed = true),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (last.destinations.isEmpty()) {
                            Text(
                                stringResource(R.string.feature_reports_savings_no_destinations),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        last.destinations.forEach { destination ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    destination.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Amount(destination.amount, currency)
                            }
                        }
                        val moved = last.destinations.fold(BigDecimal.ZERO) { sum, d -> sum + d.amount }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.feature_reports_savings_stayed),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Amount(last.net - moved, currency, tone = AmountTone.BySign)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartRow(
    name: String,
    part: NeedsWantsPart,
    currency: String,
    color: Color,
) {
    val f = formatters
    val goal = stringResource(R.string.feature_reports_savings_goal)
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(
                    part.share?.let { f.money.formatPercent(it, 0) },
                    part.target?.let { goal + " " + f.money.formatPercent(it, 0) },
                ).joinToString(" · ") + " · " + f.money.format(part.amount, currency),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressBar(
            share = part.share?.toFloat() ?: 0f,
            color = color,
            marker = part.target?.toFloat(),
            height = SMALL_BAR,
        )
    }
}

private val SMALL_CHART = 100.dp
private val SMALL_BAR = 6.dp
private const val TARGET_MIN = 0.05f
private const val TARGET_MAX = 0.5f
private const val TARGET_STEPS = 8
private const val FROM_A = 0
private const val TO_A = 1
private const val FROM_B = 2
private const val TO_B = 3
