package org.andrewdev.webwallet.feature.goals.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.andrewdev.webwallet.core.datastore.CalculatorInputs
import org.andrewdev.webwallet.core.datastore.FireInputs
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.ChartColumn
import org.andrewdev.webwallet.core.ui.ChartLine
import org.andrewdev.webwallet.core.ui.ChartMarker
import org.andrewdev.webwallet.core.ui.ColumnChart
import org.andrewdev.webwallet.core.ui.Figure
import org.andrewdev.webwallet.core.ui.LineChart
import org.andrewdev.webwallet.core.ui.ProgressBar
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.feature.goals.Calculators
import org.andrewdev.webwallet.feature.goals.GoalsUiState
import org.andrewdev.webwallet.feature.goals.GoalsViewModel
import org.andrewdev.webwallet.feature.goals.R
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

private typealias SaveInputs = ((CalculatorInputs) -> CalculatorInputs) -> Unit

/** A number the viewer typed, or the default when the field was left alone or emptied. */
private fun used(typed: String?, fallback: Double): Double = typed?.toDoubleOrNull() ?: fallback

/** What the field shows: the typed text, or the default when nothing was typed. */
private fun shown(typed: String?, fallback: Double): String = typed ?: fallback.roundToInt().toString()

/** An amount without decimals, as the calculators show their figures. */
@Composable
private fun money0(amount: Double, currency: String): String =
    formatters.money.format(BigDecimal(amount).setScale(0, RoundingMode.HALF_UP), currency, decimals = 0)

@Composable
private fun Money(amount: Double, currency: String, big: Boolean = false) {
    Amount(
        BigDecimal(amount).setScale(0, RoundingMode.HALF_UP),
        currency,
        style = if (big) WwTheme.type.amountL else WwTheme.type.amountM,
        decimals = 0,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String? = null,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ShortHistory(months: Int) {
    if (months in 1 until FEW_MONTHS) {
        WarnCard(text = pluralStringResource(R.plurals.feature_goals_short_history, months, months))
    }
}

// --- FIRE

/** FIRE (docs/04 §10.2): the capital that covers the expenses forever, and when it is reached at the current pace. */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun FireTab(state: GoalsUiState.Content, save: SaveInputs) {
    val f = formatters
    val currency = state.accountSummary?.currency?.code ?: state.baseCurrency
    val inputs = state.calculators.fire
    val averages = state.averages
    val defaults = FireDefaults(
        monthlyExpenses = (averages.monthlyExpenses ?: 0.0).roundToInt().toDouble(),
        monthlySavings = maxOf(0.0, (averages.monthlySavings ?: 0.0).roundToInt().toDouble()),
    )
    val capital = state.accountSummary?.netWorth?.toDouble() ?: 0.0
    val result = Calculators.fire(
        Calculators.FireInput(
            expenses = used(inputs.monthlyExpenses, defaults.monthlyExpenses) * MONTHS,
            withdrawalRate = used(inputs.withdrawalRate, DEFAULT_RATE) / PERCENT,
            realReturn = used(inputs.realReturn, DEFAULT_RETURN) / PERCENT,
            yearlySavings = used(inputs.monthlySavings, defaults.monthlySavings) * MONTHS,
            capital = capital,
        ),
    )
    val thisYear = f.today.year
    val savingsRate = averages.savingsRate
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ShortHistory(averages.months)
        WwCard {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.feature_goals_fire_inputs),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { save { it.copy(fire = FireInputs()) } }) {
                        Text(stringResource(R.string.feature_goals_fire_reset))
                    }
                }
                NumberField(
                    label = stringResource(R.string.feature_goals_fire_monthly_expenses),
                    value = shown(inputs.monthlyExpenses, defaults.monthlyExpenses),
                    onChange = { v -> save { it.copy(fire = it.fire.copy(monthlyExpenses = v)) } },
                    suffix = f.money.symbol(currency),
                    hint = pluralStringResource(
                        R.plurals.feature_goals_fire_expenses_hint,
                        averages.months,
                        averages.months,
                    ),
                )
                NumberField(
                    label = stringResource(R.string.feature_goals_fire_withdrawal_rate),
                    value = shown(inputs.withdrawalRate, DEFAULT_RATE),
                    onChange = { v -> save { it.copy(fire = it.fire.copy(withdrawalRate = v)) } },
                    suffix = "%",
                    hint = stringResource(R.string.feature_goals_fire_withdrawal_rate_hint),
                )
                NumberField(
                    label = stringResource(R.string.feature_goals_fire_real_return),
                    value = shown(inputs.realReturn, DEFAULT_RETURN),
                    onChange = { v -> save { it.copy(fire = it.fire.copy(realReturn = v)) } },
                    suffix = "%",
                    hint = stringResource(R.string.feature_goals_fire_real_return_hint),
                )
                NumberField(
                    label = stringResource(R.string.feature_goals_fire_monthly_savings),
                    value = shown(inputs.monthlySavings, defaults.monthlySavings),
                    onChange = { v -> save { it.copy(fire = it.fire.copy(monthlySavings = v)) } },
                    suffix = f.money.symbol(currency),
                    hint = when {
                        savingsRate == null -> null
                        savingsRate < 0 -> pluralStringResource(
                            R.plurals.feature_goals_fire_overspending_hint,
                            averages.months,
                            averages.months,
                            f.money.formatPercent(BigDecimal(-savingsRate), 0),
                        )
                        else -> pluralStringResource(
                            R.plurals.feature_goals_fire_monthly_savings_hint,
                            averages.months,
                            averages.months,
                            f.money.formatPercent(BigDecimal(savingsRate), 0),
                        )
                    },
                )
            }
        }
        WwCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                Figure(
                    label = stringResource(R.string.feature_goals_fire_number),
                    note = stringResource(
                        R.string.feature_goals_fire_number_note,
                        f.money.format(
                            BigDecimal(used(inputs.monthlyExpenses, defaults.monthlyExpenses) * MONTHS),
                            currency,
                            decimals = 0,
                        ),
                        f.money.formatPercent(BigDecimal(used(inputs.withdrawalRate, DEFAULT_RATE) / PERCENT), 0),
                    ),
                    modifier = Modifier.weight(1f),
                ) { Money(result.target, currency, big = true) }
                Figure(
                    label = stringResource(R.string.feature_goals_fire_years),
                    note = result.years?.let { stringResource(R.string.feature_goals_fire_year_note, thisYear + it) }
                        ?: stringResource(R.string.feature_goals_fire_too_far),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(result.years?.toString() ?: "—", style = WwTheme.type.amountL)
                }
            }
            Column(modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.md)) {
                Text(
                    stringResource(
                        R.string.feature_goals_fire_capital_now,
                        f.money.format(BigDecimal(capital), currency, decimals = 0),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProgressBar(
                    share = if (result.target > 0) (capital / result.target).toFloat() else 0f,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
            }
        }
        WwCard {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(R.string.feature_goals_fire_projection),
                    style = MaterialTheme.typography.titleSmall,
                )
                LineChart(
                    lines = listOf(
                        ChartLine(result.path.map { it.toFloat() }, MaterialTheme.colorScheme.primary, filled = true),
                    ),
                    markers = listOf(ChartMarker(result.target.toFloat(), WwTheme.colors.expense)),
                    include = listOf(0f),
                )
                Text(
                    "$thisYear – ${thisYear + result.path.size - 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        WwCard {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val levels = listOf(
                    Triple(R.string.feature_goals_fire_lean, R.string.feature_goals_fire_lean_note, result.levels.lean),
                    Triple(
                        R.string.feature_goals_fire_regular,
                        R.string.feature_goals_fire_regular_note,
                        result.levels.regular,
                    ),
                    Triple(R.string.feature_goals_fire_fat, R.string.feature_goals_fire_fat_note, result.levels.fat),
                    Triple(
                        R.string.feature_goals_fire_coast,
                        R.string.feature_goals_fire_coast_note,
                        result.levels.coast,
                    ),
                    Triple(
                        R.string.feature_goals_fire_barista,
                        R.string.feature_goals_fire_barista_note,
                        result.levels.barista,
                    ),
                )
                levels.forEach { (title, note, value) ->
                    val index = result.path.indexOfFirst { it >= value }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(note) + (
                                    if (index >= 0) {
                                        " · " + stringResource(R.string.feature_goals_fire_reached_in, thisYear + index)
                                    } else {
                                        ""
                                    }
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Money(value, currency)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private data class FireDefaults(val monthlyExpenses: Double, val monthlySavings: Double)

// --- FYM

/** FYM (docs/04 §10.2): how many months the liquid money lasts at the usual expenses. */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun FymTab(state: GoalsUiState.Content, save: SaveInputs) {
    val f = formatters
    val currency = state.baseCurrency
    val inputs = state.calculators.fym
    val averages = state.averages
    val list = state.accounts.filter { it.type != AccountType.CREDIT_CARD && it.type != AccountType.LOAN }
    fun liquidOf(account: Account) = inputs.accounts[account.id] ?: (account.type in GoalsViewModel.LIQUID_BY_DEFAULT)
    val liquid = list.filter(::liquidOf).sumOf { maxOf(0.0, it.balanceInBase?.toDouble() ?: 0.0) }
    val incomplete = list.filter(::liquidOf).any { it.balanceInBase == null }
    val averageExpenses = (averages.monthlyExpenses ?: 0.0).roundToInt().toDouble()
    val expenses = used(inputs.expenses, averageExpenses)
    val result = Calculators.fym(liquid, expenses, inputs.target, maxOf(0.0, averages.monthlySavings ?: 0.0))
    val months = result.months ?: 0.0
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ShortHistory(averages.months)
        WwCard {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.feature_goals_fym_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.feature_goals_fym_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (averages.monthlyExpenses == null && inputs.expenses == null) {
                    Text(
                        stringResource(R.string.feature_goals_fym_no_expenses),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        String.format(f.language.locale, "%.1f", months),
                        style = WwTheme.type.amountL,
                    )
                    Text(
                        " " + stringResource(R.string.feature_goals_fym_months_without_income),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(
                        R.string.feature_goals_fym_liquid_note,
                        f.money.format(BigDecimal(liquid), currency, decimals = 0),
                        f.money.format(BigDecimal(expenses), currency, decimals = 0),
                    ) + if (incomplete) " · " + stringResource(R.string.feature_goals_fym_incomplete) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProgressBar(
                    share = Calculators.trackPosition(months).toFloat(),
                    marker = Calculators.trackPosition(inputs.target.toDouble()).toFloat(),
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Calculators.FYM_MILESTONES.forEach { milestone ->
                        Text(
                            pluralStringResource(R.plurals.feature_goals_fym_milestone, milestone, milestone),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NumberField(
                    label = stringResource(R.string.feature_goals_fym_expenses),
                    value = shown(inputs.expenses, averageExpenses),
                    onChange = { v -> save { it.copy(fym = it.fym.copy(expenses = v)) } },
                    suffix = f.money.symbol(currency),
                    hint = if (inputs.expenses == null) {
                        pluralStringResource(
                            R.plurals.feature_goals_fym_expenses_hint,
                            averages.months,
                            averages.months,
                        )
                    } else {
                        null
                    },
                )
                if (inputs.expenses != null) {
                    TextButton(onClick = { save { it.copy(fym = it.fym.copy(expenses = null)) } }) {
                        Text(stringResource(R.string.feature_goals_fym_reset_expenses))
                    }
                }
                Text(stringResource(R.string.feature_goals_fym_target), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FYM_TARGETS.forEach { target ->
                        FilterChip(
                            selected = inputs.target == target,
                            onClick = { save { it.copy(fym = it.fym.copy(target = target)) } },
                            label = {
                                Text(
                                    pluralStringResource(R.plurals.feature_goals_fym_milestone, target, target),
                                )
                            },
                        )
                    }
                }
                Text(
                    if (result.left == 0.0) {
                        stringResource(R.string.feature_goals_fym_reached)
                    } else {
                        stringResource(R.string.feature_goals_fym_need, money0(result.need, currency)) +
                            " · " +
                            stringResource(R.string.feature_goals_fym_left, money0(result.left, currency))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val eta = result.eta
                val savings = maxOf(0.0, averages.monthlySavings ?: 0.0)
                if (result.left > 0) {
                    Text(
                        if (eta != null && savings > 0) {
                            stringResource(
                                R.string.feature_goals_fym_eta,
                                f.money.format(BigDecimal(savings), currency, decimals = 0),
                                String.format(f.language.locale, "%.1f", eta),
                            )
                        } else {
                            stringResource(R.string.feature_goals_fym_no_savings)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        WwCard {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.feature_goals_fym_liquid_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                list.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            save {
                                it.copy(
                                    fym = it.fym.copy(accounts = it.fym.accounts + (account.id to !liquidOf(account))),
                                )
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = liquidOf(account), onCheckedChange = null)
                        Text(account.name, modifier = Modifier.weight(1f).padding(start = Spacing.sm))
                        Amount(account.balance, account.currency.code, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// --- compound interest

/** The compound interest calculator (docs/04 §10.2). */
@Composable
internal fun CalculatorTab(state: GoalsUiState.Content, save: SaveInputs) {
    val f = formatters
    val currency = state.baseCurrency
    val v = state.calculators.compound
    val result = Calculators.compound(
        Calculators.CompoundInput(
            initial = used(v.initial, 0.0),
            monthly = used(v.monthly, 0.0),
            rate = used(v.rate, 0.0) / PERCENT,
            years = used(v.years, 0.0).roundToInt(),
            capitalization = Calculators.Capitalization.valueOf(v.capitalization),
            growth = used(v.growth, 0.0) / PERCENT,
            inflation = used(v.inflation, 0.0) / PERCENT,
            real = v.real,
        ),
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        WwCard {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(R.string.feature_goals_calc_parameters),
                    style = MaterialTheme.typography.titleSmall,
                )
                NumberField(
                    stringResource(R.string.feature_goals_calc_initial),
                    v.initial.orEmpty(),
                    { x -> save { it.copy(compound = it.compound.copy(initial = x)) } },
                    f.money.symbol(currency),
                )
                NumberField(
                    stringResource(R.string.feature_goals_calc_monthly),
                    v.monthly.orEmpty(),
                    { x -> save { it.copy(compound = it.compound.copy(monthly = x)) } },
                    f.money.symbol(currency),
                )
                NumberField(
                    stringResource(R.string.feature_goals_calc_rate),
                    v.rate.orEmpty(),
                    { x -> save { it.copy(compound = it.compound.copy(rate = x)) } },
                    "%",
                )
                NumberField(
                    stringResource(R.string.feature_goals_calc_years),
                    v.years.orEmpty(),
                    { x -> save { it.copy(compound = it.compound.copy(years = x)) } },
                )
                Text(
                    stringResource(R.string.feature_goals_calc_capitalization),
                    style = MaterialTheme.typography.bodySmall,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Calculators.Capitalization.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = v.capitalization == option.name,
                            onClick = { save { it.copy(compound = it.compound.copy(capitalization = option.name)) } },
                            shape = SegmentedButtonDefaults.itemShape(index, Calculators.Capitalization.entries.size),
                            label = { Text(capitalizationName(option)) },
                        )
                    }
                }
                NumberField(
                    stringResource(R.string.feature_goals_calc_growth),
                    v.growth.orEmpty(),
                    { x -> save { it.copy(compound = it.compound.copy(growth = x)) } },
                    "%",
                )
                NumberField(
                    stringResource(R.string.feature_goals_calc_inflation),
                    v.inflation.orEmpty(),
                    { x -> save { it.copy(compound = it.compound.copy(inflation = x)) } },
                    "%",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.feature_goals_calc_real), modifier = Modifier.weight(1f))
                    Switch(
                        checked = v.real,
                        onCheckedChange = { on -> save { it.copy(compound = it.compound.copy(real = on)) } },
                    )
                }
            }
        }
        WwCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                Figure(label = stringResource(R.string.feature_goals_calc_final), modifier = Modifier.weight(1f)) {
                    Money(result.balance, currency, big = true)
                }
                Figure(
                    label = stringResource(R.string.feature_goals_calc_contributed),
                    modifier = Modifier.weight(1f),
                ) {
                    Money(result.contributed, currency)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Figure(
                    label = stringResource(R.string.feature_goals_calc_interest_total),
                    modifier = Modifier.weight(1f),
                ) {
                    Money(result.interest, currency)
                }
                Figure(label = stringResource(R.string.feature_goals_calc_real_final), modifier = Modifier.weight(1f)) {
                    Money(result.realBalance, currency)
                }
            }
        }
        if (result.rows.isNotEmpty()) {
            WwCard {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.feature_goals_calc_by_year),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    ColumnChart(
                        columns = result.rows.map {
                            ChartColumn(
                                listOf(it.contributed.toFloat(), (it.balance - it.contributed).toFloat()),
                                it.year.toString(),
                            )
                        },
                        colors = listOf(MaterialTheme.colorScheme.primary, WwTheme.colors.income),
                    )
                    result.rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.feature_goals_calc_year) + " ${row.year}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Money(row.contributed, currency)
                            Text("  ")
                            Money(row.balance, currency)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun capitalizationName(option: Calculators.Capitalization): String = stringResource(
    when (option) {
        Calculators.Capitalization.MONTHLY -> R.string.feature_goals_calc_monthly_cap
        Calculators.Capitalization.QUARTERLY -> R.string.feature_goals_calc_quarterly_cap
        Calculators.Capitalization.YEARLY -> R.string.feature_goals_calc_yearly_cap
    },
)

private const val MONTHS = 12.0
private const val PERCENT = 100.0
private const val DEFAULT_RATE = 4.0
private const val DEFAULT_RETURN = 5.0
private const val FEW_MONTHS = 3
private val FYM_TARGETS = listOf(6, 12, 24)
