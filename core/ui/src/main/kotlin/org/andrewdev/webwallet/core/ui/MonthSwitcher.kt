package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

/** `‹ Жовтень 2026 ›` (docs/05 §5): the arrows step a month, the label opens a grid of months. */
@Composable
fun MonthSwitcher(month: YearMonth, onMonth: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    val f = formatters
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
            Icon(Lucide.ChevronLeft, contentDescription = stringResource(R.string.core_ui_previous_month))
        }
        Text(
            f.date.month(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable { picking = true }.padding(horizontal = Spacing.xs),
        )
        IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
            Icon(Lucide.ChevronRight, contentDescription = stringResource(R.string.core_ui_next_month))
        }
    }
    if (picking) {
        MonthPicker(
            month = month,
            onPick = {
                picking = false
                onMonth(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MonthPicker(month: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableStateOf(month.year) }
    val locale = formatters.language.locale
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { year-- }) {
                    Icon(Lucide.ChevronLeft, contentDescription = stringResource(R.string.core_ui_previous_year))
                }
                Text(year.toString(), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { year++ }) {
                    Icon(Lucide.ChevronRight, contentDescription = stringResource(R.string.core_ui_next_year))
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                maxItemsInEachRow = MONTHS_PER_ROW,
            ) {
                Month.entries.forEach { m ->
                    val candidate = YearMonth.of(year, m)
                    FilterChip(
                        selected = candidate == month,
                        onClick = { onPick(candidate) },
                        label = {
                            Text(
                                m.getDisplayName(
                                    TextStyle.SHORT_STANDALONE,
                                    locale,
                                ).replaceFirstChar { it.titlecase(locale) },
                            )
                        },
                    )
                }
            }
        }
    }
}

private const val MONTHS_PER_ROW = 4
