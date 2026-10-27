package org.andrewdev.webwallet.feature.overview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.andrewdev.webwallet.core.data.Forecast
import org.andrewdev.webwallet.core.data.MonthIncomeExpense
import org.andrewdev.webwallet.core.data.MonthSpending
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.BudgetMonth
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.CategoryIcon
import org.andrewdev.webwallet.core.ui.ChartColumn
import org.andrewdev.webwallet.core.ui.ChartLine
import org.andrewdev.webwallet.core.ui.ChartMarker
import org.andrewdev.webwallet.core.ui.ColumnChart
import org.andrewdev.webwallet.core.ui.LineChart
import org.andrewdev.webwallet.core.ui.MoneyField
import org.andrewdev.webwallet.core.ui.ProgressBar
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.feature.overview.R
import java.math.BigDecimal
import java.time.YearMonth

/** "Money to the end of the month" (docs/04 §4 item 4): actual and forecast balance, planned events, the minimum. */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun ForecastCard(
    forecast: Forecast,
    minimum: BigDecimal?,
    onMinimum: (BigDecimal?) -> Unit,
    onPlanned: () -> Unit,
) {
    val f = formatters
    val currency = forecast.currency.code
    var editing by remember { mutableStateOf(false) }
    val todayIndex = forecast.days.indexOfFirst { it.date == forecast.today }.coerceAtLeast(0)
    val balances = forecast.days.map { it.balance.toFloat() }
    val last = forecast.days.lastOrNull()
    val lowest = forecast.lowest
    val dipDay = lowest?.takeIf { minimum != null && it.balance < minimum }
    val markers = buildList {
        add(ChartMarker(0f, MaterialTheme.colorScheme.outline, dashed = false))
        if (minimum != null) add(ChartMarker(minimum.toFloat(), WwTheme.colors.warnText))
    }
    WwCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_overview_forecast_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editing = true }) {
                    Text(
                        if (minimum != null) {
                            stringResource(
                                R.string.feature_overview_forecast_minimum,
                                f.money.format(minimum, currency),
                            )
                        } else {
                            stringResource(R.string.feature_overview_forecast_set_minimum)
                        },
                    )
                }
            }
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.feature_overview_forecast_now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Amount(forecast.balance, currency, tone = AmountTone.BySign)
                }
                if (last != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(
                                R.string.feature_overview_forecast_expected,
                                f.date.short(last.date, forecast.today),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Amount(last.balance, currency, tone = AmountTone.BySign)
                    }
                }
            }
            if (balances.size > 1) {
                LineChart(
                    lines = listOf(
                        ChartLine(balances.take(todayIndex + 1), MaterialTheme.colorScheme.primary, filled = true),
                        ChartLine(
                            balances.drop(todayIndex),
                            MaterialTheme.colorScheme.primary,
                            dashed = true,
                            offset = todayIndex,
                        ),
                    ),
                    markers = markers,
                    points = balances.size,
                    include = listOf(0f),
                    description = stringResource(
                        R.string.feature_overview_forecast_aria,
                        f.money.format(forecast.balance, currency),
                        last?.let { f.date.short(it.date, forecast.today) }.orEmpty(),
                        last?.let { f.money.format(it.balance, currency) }.orEmpty(),
                    ),
                )
            }
            if (lowest != null && lowest.date != forecast.today) {
                Text(
                    stringResource(
                        R.string.feature_overview_forecast_lowest,
                        f.date.short(lowest.date, forecast.today),
                        f.money.format(lowest.balance, currency),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (minimum != null) {
                if (dipDay != null) {
                    WarnCard(
                        text = stringResource(
                            R.string.feature_overview_forecast_dip,
                            f.date.short(dipDay.date, forecast.today),
                            f.money.format(dipDay.balance, currency),
                            f.money.format(minimum, currency),
                        ),
                    )
                } else {
                    Text(
                        stringResource(R.string.feature_overview_forecast_no_dip),
                        style = MaterialTheme.typography.bodySmall,
                        color = WwTheme.colors.income,
                    )
                }
            }
            if (forecast.events.isEmpty()) {
                Text(
                    stringResource(R.string.feature_overview_forecast_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.feature_overview_forecast_planned),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                forecast.events.take(MAX_EVENTS).forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlanned),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(event.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                if (event.overdue) {
                                    stringResource(R.string.feature_overview_forecast_overdue)
                                } else {
                                    f.date.short(event.date, forecast.today)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (event.overdue) {
                                    WwTheme.colors.warnText
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Amount(event.amount, currency, tone = AmountTone.BySign, signed = true)
                    }
                }
            }
        }
    }
    if (editing) {
        MinimumSheet(
            minimum = minimum,
            currency = currency,
            onSave = {
                onMinimum(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimumSheet(minimum: BigDecimal?, currency: String, onSave: (BigDecimal?) -> Unit, onDismiss: () -> Unit) {
    val money = formatters.money
    var text by remember { mutableStateOf(minimum?.let { money.formatQuantity(it, 2).replace(' ', ' ') }.orEmpty()) }
    var value by remember { mutableStateOf(minimum) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.feature_overview_forecast_minimum_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.feature_overview_forecast_minimum_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyField(
                text = text,
                onTextChange = { text = it },
                onValueChange = { value = it },
                currency = currency,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.feature_overview_forecast_minimum_label),
                imeAction = ImeAction.Done,
                autoFocus = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (minimum != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.feature_overview_forecast_clear_minimum))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(value) }, enabled = value != null) {
                    Text(stringResource(R.string.feature_overview_forecast_save))
                }
            }
        }
    }
}

/** The biggest parent categories of the month (docs/04 §4 item 5). */
@Composable
internal fun TopCategoriesCard(
    spending: MonthSpending,
    categoriesById: Map<Long, Category>,
    month: YearMonth,
    onCategory: (id: Long, name: String, month: YearMonth) -> Unit,
) {
    val f = formatters
    val currency = spending.currency.code
    val rows = spending.rows.take(TOP_CATEGORIES)
    if (rows.isEmpty()) return
    WwCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.feature_overview_top_categories), style = MaterialTheme.typography.titleSmall)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onCategory(row.categoryId, row.name, month) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(categoriesById[row.categoryId], size = ICON)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            Text(
                                row.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text(
                                f.money.format(row.amount, currency),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        ProgressBar(
                            share = row.share.toFloat(),
                            modifier = Modifier.padding(top = Spacing.xs),
                            height = HEIGHT,
                        )
                    }
                }
            }
        }
    }
}

/** Income against expenses of the last six months (docs/04 §4 item 6). */
@Composable
internal fun SixMonthsCard(months: List<MonthIncomeExpense>) {
    val f = formatters
    if (months.isEmpty()) return
    WwCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.feature_overview_six_months), style = MaterialTheme.typography.titleSmall)
            ColumnChart(
                columns = months.map { ChartColumn(listOf(it.income.toFloat(), it.expense.toFloat()), it.month) },
                colors = listOf(WwTheme.colors.income, WwTheme.colors.expense),
                height = CHART_HEIGHT,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                months.forEach { month ->
                    Text(
                        f.date.month(YearMonth.parse(month.month)).take(MONTH_LABEL),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Legend(WwTheme.colors.income, stringResource(R.string.feature_overview_legend_income))
                Legend(WwTheme.colors.expense, stringResource(R.string.feature_overview_legend_expense))
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.padding(end = Spacing.xs).size(DOT).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The envelopes with the most spending this month (docs/04 §4 item 7). */
@Composable
internal fun EnvelopesCard(budget: BudgetMonth, progress: Float?, onBudget: () -> Unit) {
    val f = formatters
    val currency = budget.currency.code
    val envelopes = budget.flatten()
        .filter { it.children.isEmpty() && (it.assigned.signum() != 0 || it.activity.signum() != 0) }
        .sortedBy { it.activity }
        .take(TOP_ENVELOPES)
    WwCard(onClick = onBudget) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_overview_envelopes),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.feature_overview_all_envelopes),
                    style = MaterialTheme.typography.labelMedium,
                    color = WwTheme.colors.accentText,
                )
            }
            if (envelopes.isEmpty()) {
                Text(
                    stringResource(R.string.feature_overview_no_envelopes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            envelopes.forEach { row ->
                val spent = row.activity.negate()
                val budgeted = row.available + spent
                val share = when {
                    budgeted.signum() <= 0 -> if (spent.signum() > 0) 1f else 0f
                    else -> (spent.toFloat() / budgeted.toFloat())
                }
                Row {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        "${f.money.format(spent, currency)} / ${f.money.format(budgeted, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProgressBar(
                    share = share,
                    color = if (row.available.signum() < 0) {
                        WwTheme.colors.expense
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    marker = progress,
                    height = HEIGHT,
                )
            }
            if (progress != null) {
                Text(
                    stringResource(R.string.feature_overview_pace_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MAX_EVENTS = 5
private const val TOP_CATEGORIES = 6
private const val TOP_ENVELOPES = 4
private const val MONTH_LABEL = 3
private val ICON = 28.dp
private val HEIGHT = 4.dp
private val CHART_HEIGHT = 140.dp
private val DOT = 10.dp
