package org.andrewdev.webwallet.feature.budget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.BudgetMonth
import org.andrewdev.webwallet.core.model.BudgetRow
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.IncomeRow
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.BottomSheetMenu
import org.andrewdev.webwallet.core.ui.CategoryIcon
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.Figure
import org.andrewdev.webwallet.core.ui.MenuItem
import org.andrewdev.webwallet.core.ui.MonthSwitcher
import org.andrewdev.webwallet.core.ui.ProgressBar
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.StaleBanner
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.budget.BudgetModel
import org.andrewdev.webwallet.feature.budget.BudgetUiState
import org.andrewdev.webwallet.feature.budget.BudgetViewModel
import org.andrewdev.webwallet.feature.budget.Envelope
import org.andrewdev.webwallet.feature.budget.EnvelopeGroup
import org.andrewdev.webwallet.feature.budget.IncomeLine
import org.andrewdev.webwallet.feature.budget.R
import java.math.BigDecimal
import java.time.YearMonth

/** Where the budget screen sends the user. */
class BudgetActions(
    /** The transactions of a category in the month of the budget. */
    val openCategoryTransactions: (categoryId: Long, name: String, month: YearMonth) -> Unit,
)

/** What the screen asks the view model to do. */
class BudgetCallbacks(
    val onMonth: (YearMonth) -> Unit,
    val onRefresh: () -> Unit,
    val onAssign: (categoryId: Long, assigned: BigDecimal) -> Unit,
    val onMove: (from: Long, to: Long, amount: BigDecimal, onDone: (ok: Boolean) -> Unit) -> Unit,
    val onCopyPrevious: () -> Unit,
    val onPreviousAssigned: (categoryId: Long, onResult: (BigDecimal?) -> Unit) -> Unit,
)

@Composable
fun BudgetRoute(actions: BudgetActions, viewModel: BudgetViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    BudgetScreen(
        state = state,
        snackbar = snackbar,
        actions = actions,
        callbacks = BudgetCallbacks(
            onMonth = viewModel::setMonth,
            onRefresh = viewModel::refresh,
            onAssign = { id, amount -> viewModel.assign(id, amount, showError) },
            onMove = { from, to, amount, onDone ->
                viewModel.move(from, to, amount) { error ->
                    error?.let(showError)
                    onDone(error == null)
                }
            },
            onCopyPrevious = { viewModel.copyPrevious(showError) },
            onPreviousAssigned = viewModel::previousAssigned,
        ),
    )
}

/** Which sheet is open over the budget. */
private sealed interface BudgetSheet {
    data object Menu : BudgetSheet

    data object ConfirmCopy : BudgetSheet

    /** The plan of an envelope, or of an income category when [income] is set. */
    data class Assign(val envelope: Envelope, val income: Boolean = false) : BudgetSheet

    data class Move(val from: Long?, val to: Long?, val amount: BigDecimal?) : BudgetSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    state: BudgetUiState,
    snackbar: SnackbarHostState,
    actions: BudgetActions,
    callbacks: BudgetCallbacks,
) {
    var sheet by remember { mutableStateOf<BudgetSheet?>(null) }
    val content = state as? BudgetUiState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { MonthSwitcher(month = state.month, onMonth = callbacks.onMonth) },
                actions = {
                    IconButton(onClick = { sheet = BudgetSheet.Menu }, enabled = content != null && !content.busy) {
                        Icon(Lucide.EllipsisVertical, contentDescription = stringResource(R.string.feature_budget_more))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                is BudgetUiState.Loading -> SkeletonList(rows = 6)
                is BudgetUiState.Error -> ErrorState(state.error, onRetry = callbacks.onRefresh)
                is BudgetUiState.Content -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = callbacks.onRefresh,
                ) {
                    BudgetContent(
                        state = state,
                        onRefresh = callbacks.onRefresh,
                        onCategory = { actions.openCategoryTransactions(it.categoryId, it.name, state.month) },
                        onAssign = { sheet = BudgetSheet.Assign(it) },
                        onPlanIncome = { line ->
                            // The sheet's "as spent" shortcut takes the received income for an income category
                            val envelope = Envelope(
                                categoryId = line.categoryId,
                                name = line.name,
                                assigned = line.planned,
                                activity = line.received.negate(),
                                available = BigDecimal.ZERO,
                            )
                            sheet = BudgetSheet.Assign(envelope, income = true)
                        },
                        onCover = {
                            sheet = BudgetSheet.Move(from = null, to = it.categoryId, amount = it.available.negate())
                        },
                        onCopyPrevious = { sheet = BudgetSheet.ConfirmCopy },
                    )
                }
            }
        }
    }
    if (content != null) {
        BudgetSheets(
            sheet = sheet,
            state = content,
            callbacks = callbacks,
            onOpen = { sheet = it },
            onClose = { sheet = null },
        )
    }
}

@Composable
private fun BudgetSheets(
    sheet: BudgetSheet?,
    state: BudgetUiState.Content,
    callbacks: BudgetCallbacks,
    onOpen: (BudgetSheet) -> Unit,
    onClose: () -> Unit,
) {
    when (sheet) {
        null -> Unit
        BudgetSheet.Menu -> BottomSheetMenu(
            title = null,
            items = listOf(
                MenuItem(stringResource(R.string.feature_budget_copy_previous), Lucide.CopyPlus) {
                    onOpen(BudgetSheet.ConfirmCopy)
                },
                MenuItem(stringResource(R.string.feature_budget_move), Lucide.ArrowLeftRight) {
                    onOpen(BudgetSheet.Move(null, null, null))
                },
            ),
            onDismiss = onClose,
        )
        BudgetSheet.ConfirmCopy -> ConfirmDialog(
            title = stringResource(R.string.feature_budget_copy_previous),
            body = stringResource(R.string.feature_budget_copy_previous_body),
            confirmText = stringResource(R.string.feature_budget_copy_previous_confirm),
            onConfirm = {
                onClose()
                callbacks.onCopyPrevious()
            },
            onDismiss = onClose,
        )
        is BudgetSheet.Assign -> AssignSheet(
            envelope = sheet.envelope,
            asActualLabel = if (sheet.income) {
                stringResource(R.string.feature_budget_assign_as_received)
            } else {
                stringResource(R.string.feature_budget_assign_as_spent)
            },
            currency = state.data.currency.code,
            onPreviousAssigned = callbacks.onPreviousAssigned,
            onSave = { amount ->
                onClose()
                callbacks.onAssign(sheet.envelope.categoryId, amount)
            },
            onDismiss = onClose,
        )
        is BudgetSheet.Move -> MoveSheet(
            envelopes = BudgetModel.flatEnvelopes(state.data.rows),
            currency = state.data.currency.code,
            initialFrom = sheet.from,
            initialTo = sheet.to,
            initialAmount = sheet.amount,
            busy = state.busy,
            onMove = { from, to, amount -> callbacks.onMove(from, to, amount) { ok -> if (ok) onClose() } },
            onDismiss = onClose,
        )
    }
}

@Composable
private fun BudgetContent(
    state: BudgetUiState.Content,
    onRefresh: () -> Unit,
    onCategory: (Envelope) -> Unit,
    onAssign: (Envelope) -> Unit,
    onPlanIncome: (IncomeLine) -> Unit,
    onCover: (Envelope) -> Unit,
    onCopyPrevious: () -> Unit,
) {
    val currency = state.data.currency.code
    val f = formatters
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.stale) {
            item { StaleBanner(fetchedAt = state.fetchedAt, offline = state.offline, onRetry = onRefresh) }
        }
        item { ReadyCard(state.data) }
        item { FiguresCard(state.data) }
        if (state.data.unconvertedCurrencies.isNotEmpty()) {
            item {
                Text(
                    stringResource(
                        R.string.feature_budget_unconverted,
                        state.data.unconvertedCurrencies.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.overspent.isNotEmpty()) {
            item {
                val names = state.overspent.joinToString(
                    ", ",
                ) { "${it.name} ${f.money.format(it.available, currency)}" }
                WarnCard(
                    text = stringResource(R.string.feature_budget_overspent, names),
                    action = stringResource(R.string.feature_budget_cover),
                    onAction = { onCover(state.overspent.first()) },
                )
            }
        }
        if (state.isEmpty) {
            item {
                EmptyState(
                    title = stringResource(R.string.feature_budget_empty_title),
                    body = stringResource(R.string.feature_budget_empty_body),
                    action = stringResource(R.string.feature_budget_copy_previous),
                    onAction = onCopyPrevious,
                )
            }
        }
        if (state.income.isNotEmpty() || state.data.income.signum() != 0) {
            item(key = "income") { IncomeCard(state.data, state.income, state.pending, onPlan = onPlanIncome) }
        }
        state.groups.forEach { group ->
            if (!group.single) item(key = "group-${group.categoryId}") { GroupHeader(group, currency) }
            items(group.envelopes, key = { "envelope-${it.categoryId}" }) { envelope ->
                EnvelopeCard(
                    envelope = envelope,
                    state = state,
                    onCategory = { onCategory(envelope) },
                    onAssign = { onAssign(envelope) },
                )
            }
        }
    }
}

@Composable
private fun ReadyCard(data: BudgetMonth) {
    val ready = data.readyToAssign
    val hint = when {
        ready.signum() < 0 -> R.string.feature_budget_ready_negative
        ready.signum() > 0 -> R.string.feature_budget_ready_positive
        else -> R.string.feature_budget_ready_zero
    }
    WwCard {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.feature_budget_ready_to_assign),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Amount(ready, data.currency.code, style = WwTheme.type.amountL, tone = AmountTone.BySign)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FiguresCard(data: BudgetMonth) {
    val currency = data.currency.code
    WwCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Figure(label = stringResource(R.string.feature_budget_inflow), modifier = Modifier.weight(1f)) {
                Amount(data.inflow, currency)
            }
            Figure(label = stringResource(R.string.feature_budget_assigned), modifier = Modifier.weight(1f)) {
                Amount(data.assigned, currency)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Figure(label = stringResource(R.string.feature_budget_spent), modifier = Modifier.weight(1f)) {
                Amount(data.activity.negate(), currency)
            }
            Figure(label = stringResource(R.string.feature_budget_available), modifier = Modifier.weight(1f)) {
                Amount(data.available, currency, tone = AmountTone.BySign)
            }
        }
    }
}

@Composable
private fun GroupHeader(group: EnvelopeGroup, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Amount(group.available, currency, style = MaterialTheme.typography.titleSmall, tone = AmountTone.BySign)
    }
}

@Composable
private fun EnvelopeCard(
    envelope: Envelope,
    state: BudgetUiState.Content,
    onCategory: () -> Unit,
    onAssign: () -> Unit,
) {
    val currency = state.data.currency.code
    val f = formatters
    val share = BudgetModel.spentShare(envelope)
    val ahead = BudgetModel.aheadPercent(envelope, state.progress)
    val overspent = envelope.available.signum() < 0
    val pending = envelope.categoryId in state.pending
    WwCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(state.categoriesById[envelope.categoryId], size = ICON_SIZE)
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f).clickable(onClick = onCategory)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            envelope.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                        )
                        if (envelope.carryOver) {
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                stringResource(R.string.feature_budget_carry_over),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (ahead != null) {
                        Text(
                            stringResource(R.string.feature_budget_ahead, ahead),
                            style = MaterialTheme.typography.bodySmall,
                            color = WwTheme.colors.warnText,
                        )
                    }
                }
                Amount(envelope.available, currency, tone = AmountTone.BySign)
            }
            ProgressBar(
                share = share,
                color = if (overspent) WwTheme.colors.expense else MaterialTheme.colorScheme.primary,
                marker = state.progress,
                description = stringResource(
                    R.string.feature_budget_progress,
                    f.money.format(envelope.spent, currency),
                    f.money.format(envelope.assigned, currency),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_budget_plan_value, f.money.format(envelope.assigned, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pending) MaterialTheme.colorScheme.onSurfaceVariant else WwTheme.colors.accentText,
                    modifier = Modifier.clickable(
                        enabled = !pending,
                        onClick = onAssign,
                    ).padding(vertical = Spacing.xs),
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    stringResource(R.string.feature_budget_spent_value, f.money.format(envelope.spent, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val ICON_SIZE = 32.dp

@PreviewLightDark
@Composable
@Suppress("MagicNumber")
private fun BudgetPreview() {
    val rows = listOf(
        previewRow(
            1,
            "Їжа",
            "0",
            "0",
            "0",
            null,
            listOf(
                previewRow(2, "Продукти", "4000", "-3200", "800", 1),
                previewRow(3, "Кав’ярні", "2000", "-1000", "1000", 1),
            ),
        ),
        previewRow(4, "Транспорт", "1500", "-1800", "-300", null),
    )
    val data = BudgetMonth(
        month = YearMonth.of(2026, 10),
        currency = CurrencyCode.UAH,
        readyToAssign = BigDecimal("12500"),
        inflow = BigDecimal("20000"),
        assigned = BigDecimal("7500"),
        activity = BigDecimal("-6000"),
        available = BigDecimal("1500"),
        rows = rows,
        unconvertedCurrencies = emptyList(),
        plannedIncome = BigDecimal("64000"),
        income = BigDecimal("32000"),
        incomeRows = listOf(
            previewIncome(10, "Зарплата", "60000", "32000"),
            previewIncome(11, "Кешбек", "4000", "0"),
        ),
    )
    WebWalletTheme {
        BudgetScreen(
            state = BudgetUiState.Content(
                month = data.month,
                data = data,
                groups = BudgetModel.groupEnvelopes(rows),
                income = BudgetModel.incomeLines(data.incomeRows),
                overspent = BudgetModel.overspent(rows),
                progress = 0.6f,
                categoriesById = emptyMap(),
                pending = emptySet(),
                stale = false,
                fetchedAt = null,
                offline = false,
                refreshing = false,
                busy = false,
            ),
            snackbar = SnackbarHostState(),
            actions = BudgetActions { _, _, _ -> },
            callbacks = BudgetCallbacks({}, {}, { _, _ -> }, { _, _, _, _ -> }, {}, { _, _ -> }),
        )
    }
}

private fun previewIncome(id: Long, name: String, planned: String, received: String) = IncomeRow(
    categoryId = id,
    name = name,
    parentId = null,
    archived = false,
    planned = BigDecimal(planned),
    received = BigDecimal(received),
    ownPlanned = BigDecimal(planned),
    ownReceived = BigDecimal(received),
)

@Suppress("LongParameterList")
private fun previewRow(
    id: Long,
    name: String,
    assigned: String,
    activity: String,
    available: String,
    parentId: Long?,
    children: List<BudgetRow> = emptyList(),
): BudgetRow {
    val own = listOf(BigDecimal(assigned), BigDecimal(activity), BigDecimal(available))
    val total = children.fold(own) { sum, child ->
        listOf(sum[0] + child.assigned, sum[1] + child.activity, sum[2] + child.available)
    }
    return BudgetRow(id, name, parentId, false, total[0], total[1], total[2], own[0], own[1], own[2], children)
}
