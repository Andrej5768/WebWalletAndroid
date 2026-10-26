@file:Suppress("MatchingDeclarationName")

package org.andrewdev.webwallet.feature.transactions.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.Payee
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.Tag
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.ui.AccountTypeIcon
import org.andrewdev.webwallet.feature.transactions.FeedFilters
import org.andrewdev.webwallet.feature.transactions.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class FilterOptions(
    val accounts: List<Account>,
    val categories: List<Category>,
    val payees: List<Payee>,
    val tags: List<Tag>,
)

/** The filter sheet (docs/04 §6.2): account, category, payee, tag, type; Reset and Apply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    initial: FeedFilters,
    options: FilterOptions,
    lockAccount: Boolean,
    onApply: (FeedFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.feature_transactions_filters), style = MaterialTheme.typography.titleMedium)
            if (!lockAccount) {
                PickerField(
                    label = stringResource(R.string.feature_transactions_filters_account),
                    value = options.accounts.firstOrNull {
                        it.id == draft.accountId
                    }?.name ?: stringResource(R.string.feature_transactions_filters_all_accounts),
                    onClick = { picking = "account" },
                )
            }
            PickerField(
                label = stringResource(R.string.feature_transactions_filters_category),
                value = options.categories.flatMap { it.flatten() }.firstOrNull { it.id == draft.categoryId }?.name
                    ?: stringResource(R.string.feature_transactions_filters_all_categories),
                onClick = { picking = "category" },
            )
            if (draft.payeeId == null || !lockAccount) {
                PickerField(
                    label = stringResource(R.string.feature_transactions_filters_payee),
                    value = options.payees.firstOrNull {
                        it.id == draft.payeeId
                    }?.name ?: stringResource(R.string.feature_transactions_filters_all_payees),
                    onClick = { picking = "payee" },
                )
            }
            PickerField(
                label = stringResource(R.string.feature_transactions_filters_tag),
                value = options.tags.firstOrNull {
                    it.id == draft.tagId
                }?.name ?: stringResource(R.string.feature_transactions_filters_all_tags),
                onClick = { picking = "tag" },
            )
            Text(
                stringResource(R.string.feature_transactions_filters_type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val types = listOf(null) + TransactionType.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = draft.type == type,
                        onClick = { draft = draft.copy(type = type) },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                        label = { Text(typeFilterName(type)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TextButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.feature_transactions_filters_reset))
                }
                Button(onClick = {
                    onApply(draft)
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.feature_transactions_filters_apply)) }
            }
        }
    }
    when (picking) {
        "account" -> AccountPickerSheet(options.accounts, draft.accountId, allowNone = true, onPick = {
            draft = draft.copy(accountId = it)
            picking = null
        }, onDismiss = { picking = null })
        "category" -> CategoryPickerSheet(
            title = stringResource(R.string.feature_transactions_filters_category),
            categories = options.categories,
            onPick = {
                draft = draft.copy(categoryId = it.id)
                picking = null
            },
            onDismiss = { picking = null },
            allowNone = {
                draft = draft.copy(categoryId = null)
                picking = null
            },
        )
        "payee" -> NamedPickerSheet(
            title = stringResource(R.string.feature_transactions_filters_payee),
            none = stringResource(R.string.feature_transactions_filters_all_payees),
            items = options.payees.map { it.id to it.name },
            onPick = {
                draft = draft.copy(payeeId = it)
                picking = null
            },
            onDismiss = { picking = null },
        )
        "tag" -> NamedPickerSheet(
            title = stringResource(R.string.feature_transactions_filters_tag),
            none = stringResource(R.string.feature_transactions_filters_all_tags),
            items = options.tags.map { it.id to it.name },
            onPick = {
                draft = draft.copy(tagId = it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
fun typeFilterName(type: TransactionType?): String = stringResource(
    when (type) {
        null -> R.string.feature_transactions_types_all
        TransactionType.EXPENSE -> R.string.feature_transactions_types_expense
        TransactionType.INCOME -> R.string.feature_transactions_types_income
        TransactionType.TRANSFER -> R.string.feature_transactions_types_transfer
    },
)

@Composable
private fun PickerField(label: String, value: String, onClick: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/** Accounts with their type tiles; [allowNone] adds an "all accounts" row for the filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    accounts: List<Account>,
    selected: Long?,
    allowNone: Boolean,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            if (allowNone) {
                item {
                    ListItem(headlineContent = {
                        Text(stringResource(R.string.feature_transactions_filters_all_accounts))
                    }, modifier = Modifier.clickable { onPick(null) })
                }
            }
            items(accounts, key = { it.id }) { account ->
                ListItem(
                    headlineContent = { Text(account.name) },
                    supportingContent = { Text(account.currency.code) },
                    leadingContent = { AccountTypeIcon(account.type, size = 32.dp) },
                    trailingContent = if (account.id == selected) ({ Text("✓") }) else null,
                    modifier = Modifier.clickable { onPick(account.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamedPickerSheet(
    title: String,
    none: String?,
    items: List<Pair<Long, String>>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.feature_transactions_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            if (none != null && query.isBlank()) {
                item { ListItem(headlineContent = { Text(none) }, modifier = Modifier.clickable { onPick(null) }) }
            }
            items(
                items.filter { it.second.contains(query.trim(), ignoreCase = true) },
                key = { it.first },
            ) { (id, name) ->
                ListItem(headlineContent = { Text(name) }, modifier = Modifier.clickable { onPick(id) })
            }
        }
    }
}

/** From–to for the custom period, with the Material date range picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPeriodDialog(initial: Period, onPick: (Period) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.from?.toEpochMillis(),
        initialSelectedEndDateMillis = initial.to?.toEpochMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onPick(
                        Period(
                            state.selectedStartDateMillis?.toLocalDate(),
                            state.selectedEndDateMillis?.toLocalDate(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.feature_transactions_form_ok)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) { Text(stringResource(R.string.feature_transactions_form_cancel)) }
        },
    ) {
        DateRangePicker(state = state, showModeToggle = false, modifier = Modifier.padding(top = Spacing.sm))
    }
}

/** A single date for the editor, Material date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogOf(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.toLocalDate()?.let(onPick)
            }) { Text(stringResource(R.string.feature_transactions_form_ok)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) { Text(stringResource(R.string.feature_transactions_form_cancel)) }
        },
    ) { DatePicker(state = state) }
}

private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
