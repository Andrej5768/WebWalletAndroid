package org.andrewdev.webwallet.feature.transactions.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.ListFilter
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import org.andrewdev.webwallet.core.data.TransferPair
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.PeriodPreset
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.CategoryIcon
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.IconTile
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.StaleBanner
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.autoFocusOnce
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.transactions.DayGroup
import org.andrewdev.webwallet.feature.transactions.DebtDirection
import org.andrewdev.webwallet.feature.transactions.FeedFilters
import org.andrewdev.webwallet.feature.transactions.FeedRow
import org.andrewdev.webwallet.feature.transactions.FeedUiState
import org.andrewdev.webwallet.feature.transactions.FeedViewModel
import org.andrewdev.webwallet.feature.transactions.R
import java.time.YearMonth

/** How the feed was opened: the plain tab, one account, or one payee (the person of a debt). */
sealed interface FeedScope {
    data object All : FeedScope

    data class Account(val accountId: Long) : FeedScope

    data class Payee(val payeeId: Long, val name: String) : FeedScope

    /** One category in one month (`yyyy-MM`), from the budget. */
    data class Category(val categoryId: Long, val name: String, val month: String) : FeedScope
}

class FeedActions(
    val openEditor: (id: Long) -> Unit,
    val newTransaction: () -> Unit,
    val openTransferPairs: () -> Unit,
    val back: () -> Unit,
)

@Composable
fun FeedRoute(scope: FeedScope, actions: FeedActions, viewModel: FeedViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val accounts by viewModel.accountsList.collectAsStateWithLifecycle()
    val payees by viewModel.payees.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    LaunchedEffect(scope) {
        when (scope) {
            is FeedScope.Account -> viewModel.presetAccount(scope.accountId)
            is FeedScope.Payee -> viewModel.presetPayee(scope.payeeId)
            is FeedScope.Category -> viewModel.presetCategory(scope.categoryId, YearMonth.parse(scope.month))
            FeedScope.All -> Unit
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val report = rememberErrorReporter(snackbar)
    val title = when (scope) {
        FeedScope.All -> stringResource(R.string.feature_transactions_title)
        is FeedScope.Account -> accounts.firstOrNull {
            it.id == scope.accountId
        }?.name ?: stringResource(R.string.feature_transactions_title)
        is FeedScope.Payee -> scope.name
        is FeedScope.Category -> scope.name
    }
    FeedScreen(
        title = title,
        scoped = scope != FeedScope.All,
        state = state,
        filters = filters,
        query = query,
        snackbar = snackbar,
        actions = actions,
        onQueryChange = viewModel::setQuery,
        onPreset = { preset, custom -> viewModel.setPreset(preset, custom) },
        onFilters = viewModel::setFilters,
        onResetFilters = viewModel::resetFilters,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onCategorize = { transaction, categoryId -> viewModel.categorize(transaction, categoryId, report) },
        onDelete = { viewModel.delete(it, report) },
        onMerge = { viewModel.mergePair(it, report) },
        onDismissPair = { viewModel.dismissPair(it, report) },
        filterOptions = FilterOptions(accounts, (state as? FeedUiState.Content)?.categories.orEmpty(), payees, tags),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun FeedScreen(
    title: String,
    scoped: Boolean,
    state: FeedUiState,
    filters: FeedFilters,
    query: String,
    snackbar: SnackbarHostState,
    actions: FeedActions,
    onQueryChange: (String) -> Unit,
    onPreset: (PeriodPreset, org.andrewdev.webwallet.core.model.Period) -> Unit,
    onFilters: (FeedFilters) -> Unit,
    onResetFilters: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onCategorize: (Transaction, Long) -> Unit,
    onDelete: (Long) -> Unit,
    onMerge: (TransferPair) -> Unit,
    onDismissPair: (TransferPair) -> Unit,
    filterOptions: FilterOptions,
) {
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    var showFilters by remember { mutableStateOf(false) }
    var showPeriod by remember { mutableStateOf(false) }
    var categorizing by remember { mutableStateOf<Transaction?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = {
            if (searching) {
                SearchBar(query, onQueryChange, onClose = {
                    onQueryChange("")
                    searching = false
                })
            } else {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (scoped) {
                            IconButton(
                                onClick = actions.back,
                            ) { Icon(Lucide.ArrowLeft, contentDescription = null) }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = true }) {
                            Icon(
                                Lucide.Search,
                                contentDescription = stringResource(R.string.feature_transactions_search),
                            )
                        }
                        IconButton(onClick = { showFilters = true }) {
                            BadgedBox(
                                badge = { if (filters.activeCount > 0) Badge { Text(filters.activeCount.toString()) } },
                            ) {
                                Icon(
                                    Lucide.ListFilter,
                                    contentDescription = stringResource(R.string.feature_transactions_filters),
                                )
                            }
                        }
                        if (!scoped) {
                            TextButton(
                                onClick = actions.openTransferPairs,
                            ) { Text(stringResource(R.string.feature_transactions_transfer_pairs)) }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PeriodChips(
                filters.preset,
                onPick = { preset ->
                    if (preset == PeriodPreset.CUSTOM) {
                        showPeriod = true
                    } else {
                        onPreset(
                            preset,
                            filters.custom,
                        )
                    }
                },
            )
            when (state) {
                FeedUiState.Loading -> SkeletonList(rows = 6)
                is FeedUiState.Error -> ErrorState(state.error, onRetry = onRefresh)
                is FeedUiState.Content -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    FeedList(
                        state = state,
                        query = query,
                        actions = actions,
                        onRefresh = onRefresh,
                        onLoadMore = onLoadMore,
                        onCategorize = { categorizing = it },
                        onDelete = { deleting = it },
                        onMerge = onMerge,
                        onDismissPair = onDismissPair,
                    )
                }
            }
        }
    }
    if (showFilters) {
        FilterSheet(
            filters,
            filterOptions,
            lockAccount = scoped,
            onApply = {
                onFilters(it)
                showFilters = false
            },
            onReset = {
                onResetFilters()
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
    if (showPeriod) {
        CustomPeriodDialog(
            filters.custom,
            onPick = {
                onPreset(PeriodPreset.CUSTOM, it)
                showPeriod = false
            },
            onDismiss = { showPeriod = false },
        )
    }
    categorizing?.let { transaction ->
        val content = state as? FeedUiState.Content
        CategoryPickerSheet(
            title = stringResource(
                R.string.feature_transactions_choose_category_for,
                transaction.payee?.name ?: stringResource(R.string.feature_transactions_no_payee),
            ),
            categories = content?.categories.orEmpty().filter {
                it.kind.name == if (transaction.type == TransactionType.INCOME) "INCOME" else "EXPENSE"
            },
            onPick = { category ->
                onCategorize(transaction, category.id)
                categorizing = null
            },
            onDismiss = { categorizing = null },
        )
    }
    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.feature_transactions_delete_title),
            body = stringResource(R.string.feature_transactions_delete_warning),
            confirmText = stringResource(R.string.feature_transactions_delete),
            destructive = true,
            onConfirm = {
                onDelete(id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.feature_transactions_search_placeholder)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .autoFocusOnce(),
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onClose,
            ) {
                Icon(
                    Lucide.ArrowLeft,
                    contentDescription = stringResource(R.string.feature_transactions_close_search),
                )
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                ) { Icon(Lucide.X, contentDescription = null) }
            }
        },
    )
}

@Composable
fun PeriodChips(selected: PeriodPreset, onPick: (PeriodPreset) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(PeriodPreset.entries) { preset ->
            FilterChip(
                selected = preset == selected,
                onClick = { onPick(preset) },
                label = { Text(presetName(preset)) },
            )
        }
    }
}

@Composable
fun presetName(preset: PeriodPreset): String = stringResource(
    when (preset) {
        PeriodPreset.MONTH -> R.string.feature_transactions_presets_month
        PeriodPreset.PREV_MONTH -> R.string.feature_transactions_presets_prev_month
        PeriodPreset.QUARTER -> R.string.feature_transactions_presets_quarter
        PeriodPreset.YEAR -> R.string.feature_transactions_presets_year
        PeriodPreset.TWELVE_MONTHS -> R.string.feature_transactions_presets_twelve_months
        PeriodPreset.ALL -> R.string.feature_transactions_presets_all
        PeriodPreset.CUSTOM -> R.string.feature_transactions_presets_custom
    },
)

@Composable
private fun FeedList(
    state: FeedUiState.Content,
    query: String,
    actions: FeedActions,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onCategorize: (Transaction) -> Unit,
    onDelete: (Long) -> Unit,
    onMerge: (TransferPair) -> Unit,
    onDismissPair: (TransferPair) -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index ?: 0 >= info.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(nearEnd, state.hasMore) {
        snapshotFlow { nearEnd }.collect { if (it && state.hasMore) onLoadMore() }
    }
    if (state.isEmpty) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.stale) StaleBanner(state.fetchedAt, state.offline, onRefresh)
            if (query.isNotBlank()) {
                EmptyState(title = stringResource(R.string.feature_transactions_search_empty, query))
            } else {
                EmptyState(
                    title = stringResource(R.string.feature_transactions_empty_title),
                    body = stringResource(R.string.feature_transactions_empty_text),
                    action = stringResource(R.string.feature_transactions_add),
                    onAction = actions.newTransaction,
                )
            }
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (state.stale) item { StaleBanner(state.fetchedAt, state.offline, onRefresh) }
        state.days.forEach { day ->
            item(key = "day:${day.date}") { DayHeader(day) }
            items(day.transactions, key = { it.id }) { transaction ->
                val row = state.rows[transaction.id] ?: return@items
                TransactionRow(
                    row,
                    onClick = { actions.openEditor(transaction.id) },
                    onCategorize = { onCategorize(transaction) },
                    onLongClick = { onDelete(transaction.id) },
                )
                row.pair?.let { pair ->
                    PairHint(
                        row,
                        state.rows[pair.incomeId]?.account?.name,
                        pair,
                        onMerge = { onMerge(pair) },
                        onDismiss = { onDismissPair(pair) },
                    )
                }
            }
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                if (state.loadingMore) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (!state.hasMore) {
                    Text(
                        pluralStringResource(
                            R.plurals.feature_transactions_count,
                            state.totalItems.toInt(),
                            state.totalItems.toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val LOAD_MORE_AHEAD = 8

@Composable
private fun DayHeader(day: DayGroup) {
    val f = formatters
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            f.date.dayHeader(
                day.date,
                f.today,
                stringResource(R.string.feature_transactions_today),
                stringResource(R.string.feature_transactions_yesterday),
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        day.totals.forEach { (currency, amount) ->
            Amount(
                amount,
                currency,
                style = WwTheme.type.amountS,
                tone = AmountTone.BySign,
                signed = true,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

/** A row of the feed (docs/04 §6.1): the icon, the payee or category, the details line and the signed amount. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(row: FeedRow, onClick: () -> Unit, onCategorize: () -> Unit, onLongClick: () -> Unit = {}) {
    val transaction = row.transaction
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(modifier = if (row.canQuickCategorize) Modifier.clickable(onClick = onCategorize) else Modifier) {
            when {
                transaction.isTransfer -> IconTile(
                    Lucide.ArrowLeftRight,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                )
                else -> CategoryIcon(row.category)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rowTitle(row),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (transaction.isImported) {
                    Icon(
                        Lucide.Landmark,
                        contentDescription = stringResource(R.string.feature_transactions_imported_glyph),
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (transaction.recurring != null) {
                    Icon(
                        Lucide.Clock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    rowSubtitle(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Amount(
                transaction.signedAmount,
                transaction.currency.code,
                tone = AmountTone.ByType(transaction.type),
                signed = !transaction.isTransfer,
            )
            val toAmount = transaction.toAmount
            val toCurrency = transaction.toCurrency
            val foreign = toAmount != null && toCurrency != null && toCurrency != transaction.currency
            if (transaction.isTransfer && foreign) {
                Amount(
                    toAmount,
                    toCurrency.code,
                    style = WwTheme.type.amountS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun rowTitle(row: FeedRow): String {
    val t = row.transaction
    val payee = t.payee
    return when {
        row.debt != null -> payee?.name ?: stringResource(R.string.feature_transactions_no_payee)
        t.isTransfer -> "${row.account?.name.orEmpty()} → ${row.toAccount?.name.orEmpty()}"
        payee != null -> payee.name
        row.category != null -> row.category.name
        t.adjustment -> stringResource(R.string.feature_transactions_adjustment)
        else -> stringResource(R.string.feature_transactions_no_payee)
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun rowSubtitle(row: FeedRow): String {
    val t = row.transaction
    val f = formatters
    val parts = mutableListOf<String>()
    when {
        row.debt == DebtDirection.GAVE -> parts += stringResource(R.string.feature_transactions_debt_gave)
        row.debt == DebtDirection.RECEIVED -> parts += stringResource(R.string.feature_transactions_debt_received)
        t.isTransfer -> {
            val fee = t.toAmount?.let { if (t.toCurrency == t.currency && it < t.amount) t.amount - it else null }
            parts += if (fee != null) {
                stringResource(R.string.feature_transactions_transfer_fee, f.money.format(fee, t.currency.code))
            } else {
                stringResource(R.string.feature_transactions_form_type_transfer)
            }
        }
        t.adjustment -> parts += stringResource(R.string.feature_transactions_adjustment)
        t.splits.size > 1 -> parts += stringResource(R.string.feature_transactions_split, t.splits.size.toString())
        t.payee != null && row.category != null -> parts += row.category.name
        t.payee != null && row.category == null -> parts += stringResource(R.string.feature_transactions_uncategorized)
    }
    if (!t.isTransfer) row.account?.let { parts += it.name }
    if (row.tags.isNotEmpty()) parts += row.tags.joinToString(" ") { "#" + it.name }
    t.comment?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" · ")
}

/** "Looks like a transfer to …" under an expense the backend paired with an income (docs/04 §6.1). */
@Composable
private fun PairHint(
    row: FeedRow,
    toAccountName: String?,
    pair: TransferPair,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val f = formatters
    val detail = when {
        pair.exact -> stringResource(R.string.feature_transactions_pair_exact)
        pair.difference.signum() < 0 -> stringResource(
            R.string.feature_transactions_pair_less,
            f.money.format(pair.difference.abs(), row.transaction.currency.code),
        )
        else -> stringResource(
            R.string.feature_transactions_pair_more,
            f.money.format(pair.difference, row.transaction.currency.code),
        )
    }
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
        WarnCard(
            text = stringResource(
                R.string.feature_transactions_pair_text,
                row.account?.name.orEmpty(),
                toAccountName ?: "…",
            ) + " " + detail,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_transactions_pair_dismiss)) }
            TextButton(onClick = onMerge) { Text(stringResource(R.string.feature_transactions_pair_merge)) }
        }
    }
}

/** The categories of one kind as a list with their icons, for the quick action and the filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    title: String,
    categories: List<Category>,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
    allowNone: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            if (allowNone != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { allowNone() }.padding(
                            horizontal = Spacing.lg,
                            vertical = Spacing.sm,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        CategoryIcon(null, size = 32.dp)
                        Text(stringResource(R.string.feature_transactions_filters_all_categories))
                    }
                }
            }
            val flat = categories.flatMap { parent -> listOf(parent) + parent.children }.filter { !it.archived }
            flat.forEach { category ->
                item(key = category.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(category) }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                            .padding(start = if (category.parentId != null) Spacing.xxl else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        CategoryIcon(category, size = 32.dp)
                        Text(category.name)
                    }
                }
            }
        }
    }
}
