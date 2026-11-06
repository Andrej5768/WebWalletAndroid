package org.andrewdev.webwallet.feature.imports.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.CategoryKind
import org.andrewdev.webwallet.core.model.ImportOperation
import org.andrewdev.webwallet.core.model.ImportResult
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.model.TransferSuggestion
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.imports.ImportPreviewViewModel
import org.andrewdev.webwallet.feature.imports.PreviewEdits
import org.andrewdev.webwallet.feature.imports.PreviewFilter
import org.andrewdev.webwallet.feature.imports.PreviewUiState
import org.andrewdev.webwallet.feature.imports.R
import org.andrewdev.webwallet.feature.imports.RowEdit
import org.andrewdev.webwallet.feature.imports.TransferHalf

private const val LOAD_AHEAD = 10

@Composable
fun ImportPreviewRoute(
    id: String,
    onClose: () -> Unit,
    openTransactions: () -> Unit,
    viewModel: ImportPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    val leave = {
        viewModel.discard()
        onClose()
    }
    BackHandler(
        enabled = !state.confirming,
    ) { if (state.selected.isNotEmpty()) viewModel.clearSelection() else leave() }
    val result = state.result
    if (result != null) {
        ResultScreen(
            title = stringResource(R.string.feature_imports_result_title),
            stats = bankStats(result),
            warnings = result.warnings,
            linkText = stringResource(R.string.feature_imports_result_to_transactions),
            onLink = openTransactions,
            onClose = onClose,
        )
        return
    }
    PreviewScreen(
        state = state,
        onBack = leave,
        viewModel = viewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun PreviewScreen(state: PreviewUiState, onBack: () -> Unit, viewModel: ImportPreviewViewModel) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_AHEAD
        }
    }
    LaunchedEffect(nearEnd, state.pagesLoaded) { if (nearEnd) viewModel.loadMore() }
    var bulkCategory by remember { mutableStateOf(false) }
    var bulkTag by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.selected.isEmpty()) {
                            stringResource(R.string.feature_imports_preview_title)
                        } else {
                            stringResource(R.string.feature_imports_selected, state.selected.size)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (state.selected.isEmpty()) onBack else viewModel::clearSelection) {
                        Icon(if (state.selected.isEmpty()) Lucide.ArrowLeft else Lucide.X, contentDescription = null)
                    }
                },
                actions = {
                    if (state.selected.isNotEmpty()) {
                        TextButton(onClick = { bulkCategory = true }) {
                            Text(stringResource(R.string.feature_imports_bulk_category))
                        }
                        TextButton(
                            onClick = { bulkTag = true },
                        ) { Text(stringResource(R.string.feature_imports_bulk_tag)) }
                    }
                },
            )
        },
        bottomBar = {
            if (state.first != null && !state.expired) {
                Surface(tonalElevation = Spacing.xs) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
                        if (state.showErrors && state.edits.withoutTransferAccount > 0) {
                            Text(
                                stringResource(
                                    R.string.feature_imports_transfers_incomplete,
                                    state.edits.withoutTransferAccount,
                                ),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        state.confirmError?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                        val count = state.importCount.coerceAtLeast(0)
                        Button(
                            onClick = viewModel::confirm,
                            enabled = count > 0 && !state.confirming,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.confirming) CircularProgressIndicator(Modifier.padding(end = Spacing.sm))
                            Text(pluralStringResource(R.plurals.feature_imports_submit, count, count))
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.expired -> EmptyState(
                title = stringResource(R.string.feature_imports_preview_expired),
                modifier = Modifier.padding(padding),
            )
            state.loading -> SkeletonList(rows = 6, modifier = Modifier.padding(padding))
            state.error != null && state.first == null -> ErrorState(state.error, onRetry = viewModel::retry)
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item { PreviewHeader(state, viewModel) }
                items(state.visible, key = { it.key }) { operation ->
                    PreviewRowCard(
                        operation = operation,
                        edit = state.edits.editOf(operation),
                        state = state,
                        selected = operation.key in state.selected,
                        onSelect = { viewModel.toggleSelected(operation.key) },
                        onChange = { patch -> viewModel.change(operation, patch) },
                    )
                }
                if (state.loadingMore) item { SkeletonList(rows = 2) }
            }
        }
    }
    if (bulkCategory) {
        PickerSheet(
            title = stringResource(R.string.feature_imports_bulk_category),
            options = categoryOptions(state.categories, null),
            selected = null,
            onPick = { id -> id?.let { viewModel.applyBulk(it, null) } },
            onDismiss = { bulkCategory = false },
        )
    }
    if (bulkTag) {
        TagDialog(
            known = state.tags.map { it.name },
            onAdd = { viewModel.applyBulk(null, it) },
            onDismiss = { bulkTag = false },
        )
    }
}

@Composable
private fun PreviewHeader(state: PreviewUiState, viewModel: ImportPreviewViewModel) {
    val summary = state.first?.summary ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            stringResource(
                R.string.feature_imports_preview_summary,
                state.total.toInt(),
                state.duplicates.toInt(),
                state.edits.uncategorizedCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (summary.accountsCreated > 0) {
            Text(stringResource(R.string.feature_imports_preview_accounts_created, summary.accountsCreated))
        }
        if (summary.categoriesCreated > 0) {
            Text(stringResource(R.string.feature_imports_preview_categories_created, summary.categoriesCreated))
        }
        if (state.edits.possiblePairs > 0) {
            Text(
                stringResource(
                    R.string.feature_imports_preview_pairs,
                    state.edits.possiblePairs,
                    state.edits.chosenPairs,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        summary.warnings.forEach { WarnCard(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PreviewFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = {
                        Text(
                            stringResource(
                                when (filter) {
                                    PreviewFilter.ALL -> R.string.feature_imports_filter_all
                                    PreviewFilter.UNCATEGORIZED -> R.string.feature_imports_filter_uncategorized
                                    PreviewFilter.DUPLICATES -> R.string.feature_imports_filter_duplicates
                                },
                            ),
                        )
                    },
                )
            }
            if (state.selected.isNotEmpty()) {
                TextButton(
                    onClick = viewModel::selectVisible,
                ) { Text(stringResource(R.string.feature_imports_select_all)) }
            }
        }
    }
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
private fun PreviewRowCard(
    operation: ImportOperation,
    edit: RowEdit,
    state: PreviewUiState,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: ((RowEdit) -> RowEdit) -> Unit,
) {
    val duplicate = operation.duplicate
    var pickTransfer by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickPair by remember { mutableStateOf(false) }
    var addTag by remember { mutableStateOf(false) }
    val signed = if (operation.type == TransactionType.EXPENSE) operation.amount.negate() else operation.amount
    WwCard(
        modifier = Modifier
            .alpha(if (edit.include && !duplicate) 1f else DIMMED)
            .combinedClickable(enabled = !duplicate, onClick = {
                if (state.selected.isNotEmpty()) onSelect()
            }, onLongClick = onSelect),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.selected.isNotEmpty()) {
                    Checkbox(checked = selected, onCheckedChange = { onSelect() }, enabled = !duplicate)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        operation.payee ?: operation.comment ?: "—",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val created = stringResource(R.string.feature_imports_new_account)
                    val account = operation.account.name +
                        (if (operation.account.created) " ($created)" else "") +
                        (operation.toAccount?.let { " → ${it.name}" } ?: "")
                    Text(
                        formatters.date.dayMonth(operation.date) + " · " + account,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Amount(
                    amount = signed,
                    currency = operation.currency,
                    tone = if (operation.type == TransactionType.TRANSFER) AmountTone.Neutral else AmountTone.BySign,
                    signed = operation.type != TransactionType.TRANSFER,
                )
                Switch(
                    checked = edit.include && !duplicate,
                    onCheckedChange = { include -> onChange { it.copy(include = include) } },
                    enabled = !duplicate,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            if (duplicate) {
                Text(
                    stringResource(R.string.feature_imports_duplicate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PreviewEdits.allowedTypes(operation.type).forEach { type ->
                    FilterChip(
                        selected = edit.type == type,
                        onClick = { onChange { it.copy(type = type) } },
                        label = { Text(typeName(type)) },
                    )
                }
            }
            if (edit.type == TransactionType.TRANSFER && operation.type != TransactionType.TRANSFER) {
                val missing = state.showErrors && PreviewEdits.needsTransferAccount(operation, edit)
                ChoiceField(
                    label = stringResource(R.string.feature_imports_transfer_account),
                    value = state.accounts.firstOrNull { it.id == edit.transferAccountId }?.name ?: "—",
                    onClick = { pickTransfer = true },
                    error = if (missing) stringResource(R.string.feature_imports_transfer_required) else null,
                )
            }
            if (edit.type != TransactionType.TRANSFER) {
                val pairing = PreviewEdits.canPair(operation, edit)
                if (pairing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = edit.pair != null,
                            onCheckedChange = { on ->
                                val first = operation.transferSuggestions.first()
                                onChange { it.copy(pair = if (on) TransferHalf.of(first) else null) }
                            },
                        )
                        Text(
                            stringResource(R.string.feature_imports_pair_with),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (pairing && edit.pair != null) {
                    val chosen = operation.transferSuggestions.firstOrNull { TransferHalf.of(it) == edit.pair }
                    ChoiceField(
                        label = stringResource(R.string.feature_imports_pair_operation),
                        value = chosen?.let { suggestionLabel(it, operation.type == TransactionType.EXPENSE) } ?: "—",
                        onClick = { pickPair = true },
                    )
                } else {
                    val uncategorized = PreviewEdits.isUncategorized(operation, edit)
                    val categoryName = when {
                        edit.categoryId != null ->
                            state.categories.flatMap { it.flatten() }.firstOrNull { it.id == edit.categoryId }?.name
                        !edit.categoryChanged && operation.categoryName != null ->
                            stringResource(R.string.feature_imports_new_category, operation.categoryName.orEmpty())
                        else -> null
                    }
                    ChoiceField(
                        label = stringResource(R.string.feature_imports_bulk_category),
                        value = categoryName ?: stringResource(R.string.feature_imports_uncategorized),
                        onClick = { pickCategory = true },
                        warn = uncategorized,
                    )
                    if (edit.categoryChanged && edit.categoryId != null && operation.payee != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = edit.rememberRule,
                                onCheckedChange = { on -> onChange { it.copy(rememberRule = on) } },
                            )
                            Text(
                                stringResource(R.string.feature_imports_remember_rule),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                edit.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onChange { it.copy(tags = it.tags - tag) } },
                        label = { Text(tag) },
                        trailingIcon = { Icon(Lucide.X, contentDescription = null) },
                    )
                }
                TextButton(onClick = { addTag = true }) { Text(stringResource(R.string.feature_imports_add_tag)) }
            }
        }
    }
    if (pickTransfer) {
        PickerSheet(
            title = stringResource(R.string.feature_imports_transfer_account),
            options = transferOptions(state.accounts, operation),
            selected = edit.transferAccountId,
            onPick = { id -> onChange { it.copy(transferAccountId = id) } },
            onDismiss = { pickTransfer = false },
        )
    }
    if (pickCategory) {
        val kind = if (edit.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        PickerSheet(
            title = stringResource(R.string.feature_imports_bulk_category),
            options = listOf(PickerOption<Long?>(null, stringResource(R.string.feature_imports_uncategorized))) +
                categoryOptions(state.categories, kind),
            selected = edit.categoryId,
            onPick = { id -> onChange { it.copy(categoryId = id, categoryChanged = true) } },
            onDismiss = { pickCategory = false },
        )
    }
    if (pickPair) {
        val outgoing = operation.type == TransactionType.EXPENSE
        PickerSheet(
            title = stringResource(R.string.feature_imports_pair_operation),
            options = operation.transferSuggestions.map {
                PickerOption(
                    TransferHalf.of(it),
                    suggestionLabel(it, outgoing),
                )
            },
            selected = edit.pair,
            onPick = { half -> onChange { it.copy(pair = half) } },
            onDismiss = { pickPair = false },
        )
    }
    if (addTag) {
        TagDialog(
            known = state.tags.map { it.name },
            onAdd = { name ->
                onChange { current ->
                    if (current.tags.any {
                            it.equals(
                                name,
                                ignoreCase = true,
                            )
                        }
                    ) {
                        current
                    } else {
                        current.copy(tags = current.tags + name)
                    }
                }
            },
            onDismiss = { addTag = false },
        )
    }
}

private const val DIMMED = 0.5f

@Composable
private fun typeName(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.EXPENSE -> R.string.feature_imports_type_expense
        TransactionType.INCOME -> R.string.feature_imports_type_income
        TransactionType.TRANSFER -> R.string.feature_imports_type_transfer
    },
)

/** "Monobank · +5 000,00 ₴ · 20 жовт. · уже в обліку · та сама сума", like the web. */
@Composable
private fun suggestionLabel(suggestion: TransferSuggestion, outgoing: Boolean): String {
    val f = formatters
    val amount = f.money.format(
        if (outgoing) suggestion.amount else suggestion.amount.negate(),
        suggestion.currency,
        signed = true,
    )
    val source = stringResource(
        if (suggestion.transactionId != null) {
            R.string.feature_imports_pair_in_ledger
        } else {
            R.string.feature_imports_pair_in_file
        },
    )
    val difference = when {
        suggestion.exact -> stringResource(R.string.feature_imports_pair_exact)
        suggestion.difference.signum() < 0 ->
            stringResource(R.string.feature_imports_pair_less, f.money.formatPercent(suggestion.difference.abs()))
        else -> stringResource(R.string.feature_imports_pair_more, f.money.formatPercent(suggestion.difference.abs()))
    }
    return listOf(
        suggestion.accountName,
        amount,
        f.date.dayMonth(suggestion.date),
        source,
        difference,
    ).joinToString(" · ")
}

private fun transferOptions(accounts: List<Account>, operation: ImportOperation): List<PickerOption<Long?>> =
    accounts.filter { it.currency.code == operation.currency && it.id != operation.account.id }
        .map { PickerOption(it.id, it.name) }

/** Categories of one kind (or all) as a tree: parents first, their subcategories indented below. */
private fun categoryOptions(categories: List<Category>, kind: CategoryKind?): List<PickerOption<Long?>> =
    categories.filter { kind == null || it.kind == kind }
        .flatMap { it.flatten() }
        .filter { !it.archived }
        .map { PickerOption(it.id, it.name, indent = it.parentId != null) }

/** A tag by name: one of the known tags or a new one, created on confirm. */
@Composable
private fun TagDialog(known: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val matches = known.filter { name.isNotBlank() && it.contains(name.trim(), ignoreCase = true) }.take(MAX_TAG_HINTS)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_imports_add_tag)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feature_imports_tag_name)) },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    (if (name.isBlank()) known.take(MAX_TAG_HINTS) else matches).forEach { tag ->
                        FilterChip(selected = false, onClick = {
                            onAdd(tag)
                            onDismiss()
                        }, label = { Text(tag) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onAdd(name.trim())
                onDismiss()
            }) { Text(stringResource(R.string.feature_imports_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_imports_cancel)) } },
    )
}

private const val MAX_TAG_HINTS = 8

@Composable
private fun bankStats(result: ImportResult): List<Pair<String, Int>> = listOf(
    stringResource(R.string.feature_imports_result_imported) to result.transactionsImported,
    stringResource(R.string.feature_imports_result_matched) to result.transactionsMatched,
    stringResource(R.string.feature_imports_result_skipped) to result.transactionsSkipped,
    stringResource(R.string.feature_imports_result_accounts_created) to result.accountsCreated,
    stringResource(R.string.feature_imports_result_accounts_updated) to result.accountsUpdated,
    stringResource(R.string.feature_imports_result_categories_created) to result.categoriesCreated,
    stringResource(R.string.feature_imports_result_payees_created) to result.payeesCreated,
    stringResource(R.string.feature_imports_result_budgets_imported) to result.budgetsImported,
)

/** What an import did: a tile per count, the warnings, and the way to the imported data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    title: String,
    stats: List<Pair<String, Int>>,
    warnings: List<String>,
    linkText: String?,
    onLink: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Lucide.X, contentDescription = null) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                maxItemsInEachRow = 2,
            ) {
                stats.forEach { (label, value) ->
                    WwCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (warnings.isNotEmpty()) {
                Text(
                    stringResource(R.string.feature_imports_result_warnings),
                    style = MaterialTheme.typography.titleSmall,
                )
                warnings.forEach { WarnCard(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onClose) { Text(stringResource(R.string.feature_imports_result_close)) }
                if (linkText != null) Button(onClick = onLink) { Text(linkText) }
            }
        }
    }
}
