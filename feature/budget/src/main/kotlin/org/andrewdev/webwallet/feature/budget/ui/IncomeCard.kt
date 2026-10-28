package org.andrewdev.webwallet.feature.budget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.BudgetMonth
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.ProgressBar
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.feature.budget.IncomeLine
import org.andrewdev.webwallet.feature.budget.R

/**
 * The income of the month above the envelopes: per income category what came in, the plan under it and what is
 * still expected. Tapping the plan opens the same plan sheet as an envelope.
 */
@Composable
internal fun IncomeCard(
    data: BudgetMonth,
    lines: List<IncomeLine>,
    pending: Set<Long>,
    onPlan: (IncomeLine) -> Unit,
) {
    val currency = data.currency.code
    val f = formatters
    WwCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.feature_budget_income),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.feature_budget_income_columns),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider()
                IncomeLineRow(line, currency, pending = line.categoryId in pending, onPlan = { onPlan(line) })
            }
            if (data.uncategorizedIncome.signum() != 0) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.feature_budget_income_uncategorized),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Amount(data.uncategorizedIncome, currency)
                }
            }
            HorizontalDivider(thickness = TOTAL_LINE)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_budget_income_total),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(
                        R.string.feature_budget_income_of_plan,
                        f.money.format(data.income, currency),
                        f.money.format(data.plannedIncome, currency),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun IncomeLineRow(line: IncomeLine, currency: String, pending: Boolean, onPlan: () -> Unit) {
    val f = formatters
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = if (line.nested) Spacing.md else 0.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                line.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Amount(line.received, currency)
        }
        ProgressBar(
            share = line.share,
            color = WwTheme.colors.income,
            description = stringResource(
                R.string.feature_budget_income_progress,
                f.money.format(line.received, currency),
                f.money.format(line.planned, currency),
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.feature_budget_plan_value, f.money.format(line.planned, currency)),
                style = MaterialTheme.typography.bodySmall,
                color = if (pending) MaterialTheme.colorScheme.onSurfaceVariant else WwTheme.colors.accentText,
                modifier = Modifier.clickable(enabled = !pending, onClick = onPlan).padding(vertical = Spacing.xs),
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    line.planned.signum() == 0 -> stringResource(R.string.feature_budget_income_no_plan)
                    line.remaining.signum() == 0 -> stringResource(R.string.feature_budget_income_received_all)
                    else -> stringResource(
                        R.string.feature_budget_income_remaining,
                        f.money.format(line.remaining, currency),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TOTAL_LINE = 1.5.dp
