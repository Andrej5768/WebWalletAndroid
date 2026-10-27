package org.andrewdev.webwallet.feature.transactions.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.SkipForward
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.Occurrence
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.BottomSheetMenu
import org.andrewdev.webwallet.core.ui.CategoryIcon
import org.andrewdev.webwallet.core.ui.DatePickerDialogOf
import org.andrewdev.webwallet.core.ui.MenuItem
import org.andrewdev.webwallet.core.ui.MoneyField
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.transactions.PlannedActions
import org.andrewdev.webwallet.feature.transactions.PlannedRow
import org.andrewdev.webwallet.feature.transactions.PlannedUiState
import org.andrewdev.webwallet.feature.transactions.R
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Which sheet is open over an occurrence. */
private sealed interface OccurrenceSheet {
    data class Menu(val occurrence: Occurrence) : OccurrenceSheet

    data class Confirm(val occurrence: Occurrence) : OccurrenceSheet

    data class Link(val occurrence: Occurrence) : OccurrenceSheet
}

/** The pending payments pinned above the feed (docs/04 §6.1): dashed rows, overdue in the warn colour. */
@Composable
fun PlannedGroup(state: PlannedUiState, actions: PlannedActions) {
    var sheet by remember { mutableStateOf<OccurrenceSheet?>(null) }
    val f = formatters
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Clock, contentDescription = null, modifier = Modifier.padding(end = Spacing.sm))
            Text(
                pluralStringResource(
                    R.plurals.feature_transactions_planned_title,
                    state.total,
                    state.until?.let { f.date.short(it, state.today) }.orEmpty(),
                    state.total,
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        state.days.forEach { (_, rows) ->
            rows.forEach { row ->
                PlannedRowItem(
                    row = row,
                    state = state,
                    onClick = { sheet = OccurrenceSheet.Menu(row.occurrence) },
                )
            }
        }
        if (state.hidden > 0 || state.expanded) {
            TextButton(onClick = actions.toggleExpanded, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                Text(
                    if (state.expanded) {
                        stringResource(R.string.feature_transactions_planned_less)
                    } else {
                        pluralStringResource(
                            R.plurals.feature_transactions_planned_more,
                            state.hidden,
                            state.hidden,
                            state.until?.let { f.date.short(it, state.today) }.orEmpty(),
                        )
                    },
                )
            }
        }
        HorizontalDivider()
    }
    OccurrenceSheets(sheet, state, actions, onOpen = { sheet = it }, onClose = { sheet = null })
}

@Composable
private fun PlannedRowItem(row: PlannedRow, state: PlannedUiState, onClick: () -> Unit) {
    val occurrence = row.occurrence
    val f = formatters
    val warn = WwTheme.colors.warnText
    val outline = MaterialTheme.colorScheme.outline
    val busy = (occurrence.recurringId to occurrence.date) in state.busy
    val days = ChronoUnit.DAYS.between(state.today, occurrence.date).toInt()
    val due = when {
        days == 0 -> stringResource(R.string.feature_transactions_planned_today)
        days > 0 -> pluralStringResource(R.plurals.feature_transactions_planned_in_days, days, days)
        else -> pluralStringResource(R.plurals.feature_transactions_planned_days_ago, -days, -days)
    }
    val account = state.accountsById[occurrence.accountId]?.name.orEmpty()
    val detail = buildList {
        add("${f.date.short(occurrence.date, state.today)} · $due")
        if (account.isNotEmpty()) add(account)
        if (occurrence.overdue && !occurrence.autoCreate) {
            add(stringResource(R.string.feature_transactions_planned_not_found))
        }
        if (occurrence.autoCreate && !occurrence.overdue) {
            add(
                stringResource(R.string.feature_transactions_planned_auto),
            )
        }
        if (row.repeats > 1) {
            add(
                pluralStringResource(
                    R.plurals.feature_transactions_planned_repeats,
                    row.repeats,
                    row.repeats,
                    f.date.short(row.until, state.today),
                ),
            )
        }
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .drawBehind {
                val color = if (occurrence.overdue) warn else outline
                drawLine(
                    color,
                    Offset(DASH_INSET, 0f),
                    Offset(DASH_INSET, size.height),
                    strokeWidth = DASH_WIDTH,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
                )
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CategoryIcon(state.categoriesById[occurrence.categoryId])
        Column(modifier = Modifier.weight(1f)) {
            Text(
                occurrence.payee?.name ?: occurrence.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (occurrence.overdue) warn else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.padding(Spacing.xs), strokeWidth = 2.dp)
        } else {
            Amount(
                occurrence.signedAmount,
                occurrence.currency.code,
                tone = AmountTone.ByType(occurrence.type),
                signed = occurrence.type != TransactionType.TRANSFER,
            )
        }
    }
}

@Composable
private fun OccurrenceSheets(
    sheet: OccurrenceSheet?,
    state: PlannedUiState,
    actions: PlannedActions,
    onOpen: (OccurrenceSheet) -> Unit,
    onClose: () -> Unit,
) {
    when (sheet) {
        null -> Unit
        is OccurrenceSheet.Menu -> BottomSheetMenu(
            title = sheet.occurrence.name,
            items = listOf(
                MenuItem(stringResource(R.string.feature_transactions_planned_confirm), Lucide.Save) {
                    if (sheet.occurrence.variableAmount) {
                        onOpen(OccurrenceSheet.Confirm(sheet.occurrence))
                    } else {
                        actions.confirm(sheet.occurrence, null, null)
                    }
                },
                MenuItem(stringResource(R.string.feature_transactions_planned_link), Lucide.Link) {
                    onOpen(OccurrenceSheet.Link(sheet.occurrence))
                },
                MenuItem(stringResource(R.string.feature_transactions_planned_skip), Lucide.SkipForward) {
                    actions.skip(sheet.occurrence)
                },
                MenuItem(stringResource(R.string.feature_transactions_planned_edit_series), Lucide.Pencil) {
                    actions.editSeries(sheet.occurrence.recurringId)
                },
            ),
            onDismiss = onClose,
        )
        is OccurrenceSheet.Confirm -> ConfirmSheet(
            occurrence = sheet.occurrence,
            today = state.today,
            onConfirm = { date, amount ->
                onClose()
                actions.confirm(sheet.occurrence, date, amount)
            },
            onDismiss = onClose,
        )
        is OccurrenceSheet.Link -> LinkSheet(
            occurrence = sheet.occurrence,
            state = state,
            candidates = actions.candidates,
            onLink = { id ->
                onClose()
                actions.link(sheet.occurrence, id)
            },
            onDismiss = onClose,
        )
    }
}

/** A payment whose amount varies: the actual amount and date before it is saved (web `ConfirmModal`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(
    occurrence: Occurrence,
    today: LocalDate,
    onConfirm: (LocalDate, BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = formatters.money
    var text by remember { mutableStateOf(money.formatQuantity(occurrence.amount, 2).replace(' ', ' ')) }
    var amount by remember { mutableStateOf<BigDecimal?>(occurrence.amount) }
    var date by remember { mutableStateOf(if (occurrence.date.isAfter(today)) today else occurrence.date) }
    var pickDate by remember { mutableStateOf(false) }
    val f = formatters
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.feature_transactions_planned_confirm_title, occurrence.name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.feature_transactions_planned_confirm_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyField(
                text = text,
                onTextChange = { text = it },
                onValueChange = { amount = it },
                currency = occurrence.currency.code,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.feature_transactions_form_amount),
                imeAction = ImeAction.Done,
                autoFocus = true,
            )
            OutlinedTextField(
                value = f.date.long(date),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.feature_transactions_form_date)) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().clickable { pickDate = true },
            )
            Button(
                onClick = { amount?.let { onConfirm(date, it) } },
                enabled = (amount?.signum() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_transactions_planned_confirm))
            }
        }
    }
    if (pickDate) {
        DatePickerDialogOf(date, onPick = {
            date = it
            pickDate = false
        }, onDismiss = { pickDate = false })
    }
}

/** The transactions that may have paid the occurrence; one is chosen (web `LinkModal`, without the search). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSheet(
    occurrence: Occurrence,
    state: PlannedUiState,
    candidates: (Occurrence, onResult: (ApiResult<List<Transaction>>) -> Unit) -> Unit,
    onLink: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var result by remember { mutableStateOf<ApiResult<List<Transaction>>?>(null) }
    var selected by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(occurrence) { candidates(occurrence) { result = it } }
    val f = formatters
    val account = state.accountsById[occurrence.accountId]?.name.orEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = Spacing.xxl)) {
            Text(
                stringResource(R.string.feature_transactions_planned_link),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            Text(
                stringResource(
                    R.string.feature_transactions_planned_link_planned,
                    f.date.short(occurrence.date, state.today),
                    account,
                    f.money.format(occurrence.amount, occurrence.currency.code),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
            when (val loaded = result) {
                null -> CircularProgressIndicator(modifier = Modifier.padding(Spacing.lg))
                is ApiResult.Err -> Text(
                    loaded.error.message(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(Spacing.lg),
                )
                is ApiResult.Ok -> if (loaded.value.isEmpty()) {
                    Text(
                        stringResource(R.string.feature_transactions_planned_link_none),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(loaded.value, key = { it.id }) { transaction ->
                            CandidateRow(
                                transaction = transaction,
                                occurrence = occurrence,
                                state = state,
                                selected = transaction.id == selected,
                                onSelect = { selected = transaction.id },
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { selected?.let(onLink) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            ) {
                Text(stringResource(R.string.feature_transactions_planned_link_submit))
            }
        }
    }
}

@Composable
private fun CandidateRow(
    transaction: Transaction,
    occurrence: Occurrence,
    state: PlannedUiState,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val f = formatters
    val days = ChronoUnit.DAYS.between(occurrence.date, transaction.date).toInt()
    val diff = transaction.amount - occurrence.amount
    val notes = buildList {
        add(
            stringResource(
                if (transaction.accountId == occurrence.accountId) {
                    R.string.feature_transactions_planned_link_same_account
                } else {
                    R.string.feature_transactions_planned_link_other_account
                },
            ),
        )
        when {
            days < 0 -> add(pluralStringResource(R.plurals.feature_transactions_planned_link_earlier, -days, -days))
            days > 0 -> add(pluralStringResource(R.plurals.feature_transactions_planned_link_later, days, days))
        }
        if (diff.signum() != 0) {
            add(
                stringResource(
                    if (diff.signum() > 0) {
                        R.string.feature_transactions_planned_link_more_by
                    } else {
                        R.string.feature_transactions_planned_link_less_by
                    },
                    f.money.format(diff.abs(), transaction.currency.code),
                ),
            )
        }
    }.joinToString(", ")
    ListItem(
        headlineContent = {
            val category = state.categoriesById[transaction.categoryId]?.name
            Text(transaction.payee?.name ?: category ?: transaction.comment.orEmpty())
        },
        supportingContent = { Text("${f.date.short(transaction.date, state.today)} · $notes") },
        leadingContent = { RadioButton(selected = selected, onClick = onSelect) },
        trailingContent = { Amount(transaction.amount, transaction.currency.code) },
        modifier = Modifier.clickable(onClick = onSelect),
    )
}

private const val DASH_INSET = 6f
private const val DASH_WIDTH = 4f
private const val DASH_ON = 10f
private const val DASH_OFF = 6f
