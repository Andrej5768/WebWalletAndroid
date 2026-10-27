package org.andrewdev.webwallet.feature.goals.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import org.andrewdev.webwallet.core.datastore.CalculatorInputs
import org.andrewdev.webwallet.core.designsystem.icons.CategoryIcons
import org.andrewdev.webwallet.core.designsystem.theme.PaletteColor
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.Goal
import org.andrewdev.webwallet.core.model.GoalStatus
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.BottomSheetMenu
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.Figure
import org.andrewdev.webwallet.core.ui.IconTile
import org.andrewdev.webwallet.core.ui.MenuItem
import org.andrewdev.webwallet.core.ui.MoneyField
import org.andrewdev.webwallet.core.ui.ProgressBar
import org.andrewdev.webwallet.core.ui.RingProgress
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.StaleBanner
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.goals.GoalsActions
import org.andrewdev.webwallet.feature.goals.GoalsUiState
import org.andrewdev.webwallet.feature.goals.GoalsViewModel
import org.andrewdev.webwallet.feature.goals.R
import java.math.BigDecimal
import java.util.UUID

private enum class GoalsTab(val title: Int) {
    GOALS(R.string.feature_goals_tab_goals),
    FIRE(R.string.feature_goals_tab_fire),
    FYM(R.string.feature_goals_tab_fym),
    CALC(R.string.feature_goals_tab_calc),
}

@Composable
fun GoalsRoute(actions: GoalsActions, viewModel: GoalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    GoalsScreen(
        state = state,
        snackbar = snackbar,
        actions = actions,
        onRefresh = viewModel::refresh,
        onSetAchieved = { goal, achieved -> viewModel.setAchieved(goal, achieved, showError) },
        onDelete = { viewModel.delete(it, showError) },
        onContribute = { goal, amount, key -> viewModel.contribute(goal, amount, key, showError) },
        onSaveCalculators = viewModel::saveCalculators,
    )
}

/** The goals tabs (docs/04 §10): Goals · FIRE · FYM · Calculator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    snackbar: SnackbarHostState,
    actions: GoalsActions,
    onRefresh: () -> Unit,
    onSetAchieved: (Goal, Boolean) -> Unit,
    onDelete: (Goal) -> Unit,
    onContribute: (Goal, BigDecimal, UUID) -> Unit,
    onSaveCalculators: ((CalculatorInputs) -> CalculatorInputs) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_goals_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.back) { Icon(Lucide.ArrowLeft, contentDescription = null) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 0 && state is GoalsUiState.Content) {
                FloatingActionButton(onClick = { actions.openEditor(null) }) {
                    Icon(Lucide.Plus, contentDescription = stringResource(R.string.feature_goals_add))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tab) {
                GoalsTab.entries.forEachIndexed { index, item ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(item.title)) })
                }
            }
            when (state) {
                GoalsUiState.Loading -> SkeletonList(rows = 4)
                is GoalsUiState.Error -> ErrorState(state.error, onRetry = onRefresh)
                is GoalsUiState.Content -> when (GoalsTab.entries[tab]) {
                    GoalsTab.GOALS -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        GoalsList(state, actions, onRefresh, onSetAchieved, onDelete, onContribute)
                    }
                    GoalsTab.FIRE -> FireTab(state, onSaveCalculators)
                    GoalsTab.FYM -> FymTab(state, onSaveCalculators)
                    GoalsTab.CALC -> CalculatorTab(state, onSaveCalculators)
                }
            }
        }
    }
}

/** Which sheet is open over a goal. */
private sealed interface GoalSheet {
    data class Menu(val goal: Goal) : GoalSheet

    data class Contribute(val goal: Goal) : GoalSheet

    data class Delete(val goal: Goal) : GoalSheet
}

@Composable
private fun GoalsList(
    state: GoalsUiState.Content,
    actions: GoalsActions,
    onRefresh: () -> Unit,
    onSetAchieved: (Goal, Boolean) -> Unit,
    onDelete: (Goal) -> Unit,
    onContribute: (Goal, BigDecimal, UUID) -> Unit,
) {
    var sheet by remember { mutableStateOf<GoalSheet?>(null) }
    val summary = state.summary
    val currency = summary.currency.code
    val f = formatters
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = FAB_SPACE),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.stale) item { StaleBanner(state.fetchedAt, state.offline, onRefresh) }
        item {
            WwCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure(
                        label = stringResource(R.string.feature_goals_saved),
                        note = stringResource(
                            R.string.feature_goals_saved_of,
                            f.money.format(summary.target, currency),
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Amount(summary.saved, currency) }
                    Figure(
                        label = stringResource(R.string.feature_goals_monthly),
                        note = stringResource(R.string.feature_goals_monthly_note),
                        modifier = Modifier.weight(1f),
                    ) { Amount(summary.monthlyNeeded, currency) }
                }
                val savings = state.averages.monthlySavings
                Figure(
                    label = stringResource(R.string.feature_goals_average_savings),
                    note = if (savings != null && state.averages.savingsRate != null) {
                        pluralStringResource(
                            R.plurals.feature_goals_average_savings_note,
                            state.averages.months,
                            f.money.formatPercent(BigDecimal(state.averages.savingsRate), 0),
                            state.averages.months,
                        )
                    } else {
                        stringResource(R.string.feature_goals_average_savings_empty)
                    },
                ) { Amount(BigDecimal(savings ?: 0.0).setScale(0, java.math.RoundingMode.HALF_UP), state.baseCurrency) }
            }
        }
        if (summary.unconvertedCurrencies.isNotEmpty()) {
            item {
                WarnCard(
                    text = stringResource(
                        R.string.feature_goals_unconverted,
                        summary.unconvertedCurrencies.joinToString(", "),
                    ),
                )
            }
        }
        if (state.isEmpty) {
            item {
                EmptyState(
                    title = stringResource(R.string.feature_goals_empty),
                    action = stringResource(R.string.feature_goals_add),
                    onAction = { actions.openEditor(null) },
                )
            }
        }
        items(summary.goals, key = { it.id }) { goal ->
            GoalCard(
                goal = goal,
                accountName = goal.accountId?.let { state.accountsById[it]?.name },
                busy = goal.id in state.busy,
                onClick = { sheet = GoalSheet.Menu(goal) },
            )
        }
    }
    when (val open = sheet) {
        null -> Unit
        is GoalSheet.Menu -> BottomSheetMenu(
            title = open.goal.name,
            items = buildList {
                add(MenuItem(stringResource(R.string.feature_goals_edit)) { actions.openEditor(open.goal.id) })
                if (open.goal.accountId == null) {
                    add(
                        MenuItem(
                            stringResource(R.string.feature_goals_contribute),
                        ) { sheet = GoalSheet.Contribute(open.goal) },
                    )
                }
                add(
                    MenuItem(
                        stringResource(
                            if (open.goal.achieved) {
                                R.string.feature_goals_mark_active
                            } else {
                                R.string.feature_goals_mark_achieved
                            },
                        ),
                    ) { onSetAchieved(open.goal, !open.goal.achieved) },
                )
                add(
                    MenuItem(
                        stringResource(R.string.feature_goals_delete),
                        destructive = true,
                    ) { sheet = GoalSheet.Delete(open.goal) },
                )
            },
            onDismiss = { sheet = null },
        )
        is GoalSheet.Contribute -> ContributionSheet(
            goal = open.goal,
            onSubmit = { amount, key ->
                sheet = null
                onContribute(open.goal, amount, key)
            },
            onDismiss = { sheet = null },
        )
        is GoalSheet.Delete -> ConfirmDialog(
            title = stringResource(R.string.feature_goals_delete_title, open.goal.name),
            body = stringResource(R.string.feature_goals_delete_text),
            confirmText = stringResource(R.string.feature_goals_delete),
            destructive = true,
            onConfirm = {
                sheet = null
                onDelete(open.goal)
            },
            onDismiss = { sheet = null },
        )
    }
}

@Composable
private fun GoalCard(goal: Goal, accountName: String?, busy: Boolean, onClick: () -> Unit) {
    val f = formatters
    val currency = goal.currency.code
    val dark = isSystemInDarkTheme()
    val palette = PaletteColor.of(goal.color, goal.id)
    val icon = CategoryIcons[goal.icon]
    val share = goal.progress.toFloat()
    val paceShare = goal.expected?.let { expected ->
        if (goal.targetAmount.signum() > 0) (expected / goal.targetAmount).toFloat() else null
    }
    WwCard(onClick = onClick) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, tint = palette.color(dark), container = palette.container(dark))
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            goal.targetDate?.let {
                                stringResource(
                                    R.string.feature_goals_until,
                                    f.date.short(it, f.today),
                                )
                            },
                            accountName
                                ?: stringResource(R.string.feature_goals_manual).takeIf { goal.accountId == null },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RingProgress(
                    share = share,
                    color = palette.color(dark),
                    label = f.money.formatPercent(goal.progress, 0),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Amount(goal.saved, currency, style = WwTheme.type.amountM)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    stringResource(R.string.feature_goals_progress, f.money.format(goal.targetAmount, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                SuggestionChip(onClick = onClick, label = { Text(statusName(goal.status)) }, enabled = !busy)
            }
            ProgressBar(share = share, color = palette.color(dark), marker = paceShare)
            Text(
                paceText(goal),
                style = MaterialTheme.typography.bodySmall,
                color = if (goal.status == GoalStatus.BEHIND || goal.status == GoalStatus.OVERDUE) {
                    WwTheme.colors.warnText
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            goal.monthlyNeeded?.let { monthly ->
                Text(
                    (
                        goal.monthsLeft?.let { pluralStringResource(R.plurals.feature_goals_monthly_label, it, it) }
                            ?: stringResource(R.string.feature_goals_monthly_now)
                        ) + ": " + f.money.format(monthly, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun paceText(goal: Goal): String {
    val f = formatters
    val currency = goal.currency.code
    fun money(amount: BigDecimal) = f.money.format(amount, currency)
    return when (goal.status) {
        GoalStatus.ACHIEVED -> goal.achievedOn?.let {
            stringResource(R.string.feature_goals_pace_achieved_on, f.date.short(it, f.today))
        } ?: stringResource(R.string.feature_goals_pace_achieved)
        GoalStatus.NO_DATE -> stringResource(R.string.feature_goals_pace_no_date, money(goal.remaining))
        GoalStatus.OVERDUE -> stringResource(R.string.feature_goals_pace_overdue, money(goal.remaining))
        GoalStatus.ON_TRACK, GoalStatus.BEHIND -> {
            val expected = goal.expected ?: BigDecimal.ZERO
            val ahead = goal.saved - expected
            when {
                ahead.signum() > 0 -> stringResource(R.string.feature_goals_pace_ahead, money(ahead))
                ahead.signum() == 0 -> stringResource(R.string.feature_goals_pace_on_pace)
                else -> stringResource(R.string.feature_goals_pace_behind, money(expected), money(ahead.negate()))
            }
        }
    }
}

@Composable
fun statusName(status: GoalStatus): String = stringResource(
    when (status) {
        GoalStatus.ACHIEVED -> R.string.feature_goals_status_achieved
        GoalStatus.NO_DATE -> R.string.feature_goals_status_no_date
        GoalStatus.ON_TRACK -> R.string.feature_goals_status_on_track
        GoalStatus.BEHIND -> R.string.feature_goals_status_behind
        GoalStatus.OVERDUE -> R.string.feature_goals_status_overdue
    },
)

/** Adding to or taking from a goal tracked by hand (docs/04 §10.1 contribution sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributionSheet(goal: Goal, onSubmit: (BigDecimal, UUID) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf<BigDecimal?>(null) }
    var withdraw by remember { mutableStateOf(false) }
    val key = remember { UUID.randomUUID() }
    val f = formatters
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.feature_goals_contribution_title, goal.name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.feature_goals_contribution_hint,
                    f.money.format(goal.saved, goal.currency.code),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyField(
                text = text,
                onTextChange = { text = it },
                onValueChange = { amount = it },
                currency = goal.currency.code,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.feature_goals_contribution_amount),
                imeAction = ImeAction.Done,
                autoFocus = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_goals_contribution_withdraw),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = withdraw, onCheckedChange = { withdraw = it })
            }
            Button(
                onClick = { amount?.let { onSubmit(if (withdraw) it.negate() else it, key) } },
                enabled = (amount?.signum() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_goals_contribute))
            }
        }
    }
}

private val FAB_SPACE = 96.dp
