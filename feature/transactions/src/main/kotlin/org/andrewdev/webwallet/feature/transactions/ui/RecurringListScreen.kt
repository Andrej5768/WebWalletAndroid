package org.andrewdev.webwallet.feature.transactions.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.Frequency
import org.andrewdev.webwallet.core.model.Recurring
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.Figure
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.StaleBanner
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.feature.transactions.R
import org.andrewdev.webwallet.feature.transactions.RecurringListUiState
import org.andrewdev.webwallet.feature.transactions.RecurringListViewModel
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun RecurringListRoute(
    onOpen: (id: Long?) -> Unit,
    onBack: () -> Unit,
    viewModel: RecurringListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecurringListScreen(state = state, onOpen = onOpen, onBack = onBack, onRefresh = viewModel::refresh)
}

/** The recurring payments (docs/04 §6.4 list): monthly totals, the active series, the stopped ones at the end. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringListScreen(
    state: RecurringListUiState,
    onOpen: (id: Long?) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_transactions_recurring_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { onOpen(null) }) {
                        Icon(
                            Lucide.Plus,
                            contentDescription = stringResource(R.string.feature_transactions_recurring_add),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                RecurringListUiState.Loading -> SkeletonList(rows = 5)
                is RecurringListUiState.Error -> ErrorState(state.error, onRetry = onRefresh)
                is RecurringListUiState.Content -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                ) {
                    RecurringContent(state, onOpen, onRefresh)
                }
            }
        }
    }
}

@Composable
private fun RecurringContent(state: RecurringListUiState.Content, onOpen: (Long?) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        if (state.stale) item { StaleBanner(state.fetchedAt, state.offline, onRefresh) }
        item {
            WwCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure(
                        label = stringResource(R.string.feature_transactions_recurring_monthly_out),
                        modifier = Modifier.weight(1f),
                    ) { Amount(state.monthlyOut, state.baseCurrency, color = WwTheme.colors.expense) }
                    Figure(
                        label = stringResource(R.string.feature_transactions_recurring_monthly_in),
                        modifier = Modifier.weight(1f),
                    ) { Amount(state.monthlyIn, state.baseCurrency, color = WwTheme.colors.income) }
                    Figure(
                        label = stringResource(R.string.feature_transactions_recurring_active),
                        modifier = Modifier.weight(1f),
                    ) { Text(state.active.size.toString(), style = WwTheme.type.amountM) }
                }
                Text(
                    stringResource(R.string.feature_transactions_recurring_monthly_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.md),
                )
            }
        }
        if (state.isEmpty) {
            item {
                EmptyState(
                    title = stringResource(R.string.feature_transactions_recurring_empty),
                    action = stringResource(R.string.feature_transactions_recurring_add),
                    onAction = { onOpen(null) },
                )
            }
        }
        items(state.active, key = { it.id }) { RecurringRow(it, state, onClick = { onOpen(it.id) }) }
        if (state.stopped.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.feature_transactions_recurring_stopped),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
            items(state.stopped, key = { "stopped-${it.id}" }) { RecurringRow(it, state, onClick = { onOpen(it.id) }) }
        }
    }
}

@Composable
private fun RecurringRow(item: Recurring, state: RecurringListUiState.Content, onClick: () -> Unit) {
    val f = formatters
    val today = f.today
    val account = state.accountsById[item.accountId]?.name.orEmpty()
    val nextDate = item.nextDate
    val endDate = item.endDate
    val timing = when {
        nextDate != null -> stringResource(R.string.feature_transactions_recurring_next, f.date.short(nextDate, today))
        endDate != null ->
            stringResource(R.string.feature_transactions_recurring_stopped_on, f.date.short(endDate, today))
        else -> null
    }
    val subtitle = listOfNotNull(scheduleText(item.frequency, item.interval, item.startDate), account, timing)
        .joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.autoCreate) {
                    SuggestionChip(
                        onClick = onClick,
                        label = { Text(stringResource(R.string.feature_transactions_recurring_auto)) },
                    )
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val signed = if (item.type == TransactionType.EXPENSE) item.amount.negate() else item.amount
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.variableAmount) Text("≈ ", style = WwTheme.type.amountM)
            Amount(
                signed,
                item.currency.code,
                tone = AmountTone.ByType(item.type),
                signed = item.type != TransactionType.TRANSFER,
            )
        }
    }
}

/** "щомісяця 24-го", "кожні 2 тижні, пн", "щороку 1 січ.", "щодня" (web `scheduleText`). */
@Composable
fun scheduleText(frequency: Frequency, interval: Int, startDate: LocalDate): String {
    val f = formatters
    val locale = f.language.locale
    val detail = when (frequency) {
        Frequency.DAILY -> ""
        Frequency.WEEKLY -> startDate.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        Frequency.MONTHLY -> stringResource(R.string.feature_transactions_schedule_day_of_month, startDate.dayOfMonth)
        Frequency.YEARLY -> f.date.dayMonth(startDate)
    }
    val every = if (interval == 1) {
        stringResource(everyOne(frequency))
    } else {
        pluralStringResource(everyN(frequency), interval, interval)
    }
    return if (detail.isEmpty()) every else "$every $detail"
}

private fun everyOne(frequency: Frequency): Int = when (frequency) {
    Frequency.DAILY -> R.string.feature_transactions_schedule_every_day
    Frequency.WEEKLY -> R.string.feature_transactions_schedule_every_week
    Frequency.MONTHLY -> R.string.feature_transactions_schedule_every_month
    Frequency.YEARLY -> R.string.feature_transactions_schedule_every_year
}

private fun everyN(frequency: Frequency): Int = when (frequency) {
    Frequency.DAILY -> R.plurals.feature_transactions_schedule_every_n_days
    Frequency.WEEKLY -> R.plurals.feature_transactions_schedule_every_n_weeks
    Frequency.MONTHLY -> R.plurals.feature_transactions_schedule_every_n_months
    Frequency.YEARLY -> R.plurals.feature_transactions_schedule_every_n_years
}
