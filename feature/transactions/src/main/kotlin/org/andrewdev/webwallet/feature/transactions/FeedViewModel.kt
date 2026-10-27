package org.andrewdev.webwallet.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.CacheState
import org.andrewdev.webwallet.core.data.CatalogRepository
import org.andrewdev.webwallet.core.data.FeedFilter
import org.andrewdev.webwallet.core.data.TransactionsRepository
import org.andrewdev.webwallet.core.data.TransferPair
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.Payee
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.PeriodPreset
import org.andrewdev.webwallet.core.model.Tag
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** One day of the feed with its net change per currency, transfers excluded (web `groupByDay`). */
data class DayGroup(
    val date: LocalDate,
    val transactions: List<Transaction>,
    val totals: List<Pair<String, BigDecimal>>,
)

/** What a feed row shows besides the transaction: names resolved from the catalog. */
data class FeedRow(
    val transaction: Transaction,
    val category: Category?,
    val account: Account?,
    val toAccount: Account?,
    val tags: List<Tag>,
    /** A transfer with a debt account on one side (docs/04 Debts). */
    val debt: DebtDirection?,
    /** The suggested transfer pair this expense is part of. */
    val pair: TransferPair?,
) {
    val canQuickCategorize: Boolean
        get() = transaction.type != TransactionType.TRANSFER && transaction.categoryId == null &&
            !transaction.isSplit && !transaction.adjustment
}

enum class DebtDirection { GAVE, RECEIVED }

/** Which way a transfer with a debt account goes, or null for any other transaction (web `debtDirection`). */
@Suppress("ReturnCount")
fun debtDirection(transaction: Transaction, debtAccountIds: Set<Long>): DebtDirection? {
    val to = transaction.toAccountId ?: return null
    if (transaction.type != TransactionType.TRANSFER) return null
    val fromDebt = transaction.accountId in debtAccountIds
    val toDebt = to in debtAccountIds
    if (fromDebt == toDebt) return null
    return if (toDebt) DebtDirection.GAVE else DebtDirection.RECEIVED
}

sealed interface FeedUiState {
    data object Loading : FeedUiState

    data class Error(val error: ApiError) : FeedUiState

    data class Content(
        val days: List<DayGroup>,
        val rows: Map<Long, FeedRow>,
        val totalItems: Long,
        val hasMore: Boolean,
        val loadingMore: Boolean,
        val refreshing: Boolean,
        val stale: Boolean,
        val fetchedAt: Instant?,
        val offline: Boolean,
        val categories: List<Category>,
    ) : FeedUiState {
        val isEmpty: Boolean get() = days.isEmpty()
    }
}

data class FeedFilters(
    val preset: PeriodPreset = PeriodPreset.MONTH,
    val custom: Period = Period.ALL,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val payeeId: Long? = null,
    val tagId: Long? = null,
    val type: TransactionType? = null,
) : java.io.Serializable {
    fun toFilter(today: LocalDate, query: String?): FeedFilter = FeedFilter(
        period = Period.of(preset, today, custom),
        accountId = accountId,
        categoryId = categoryId,
        payeeId = payeeId,
        tagId = tagId,
        type = type,
        query = query?.trim()?.ifEmpty { null },
    )

    val activeCount: Int get() = listOfNotNull(accountId, categoryId, payeeId, tagId, type).size
}

/** The feed (docs/04 §6.1): the period chips, the filter sheet, search, paging, transfer-pair hints. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val transactions: TransactionsRepository,
    private val accounts: AccountsRepository,
    private val catalog: CatalogRepository,
    private val clock: KyivClock,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val filtersState = MutableStateFlow(savedState.get<FeedFilters>(KEY_FILTERS) ?: FeedFilters())
    private val queryState = MutableStateFlow(savedState.get<String>(KEY_QUERY).orEmpty())
    private val loadingMore = MutableStateFlow(false)
    private val pairs = MutableStateFlow<List<TransferPair>>(emptyList())

    val filters: StateFlow<FeedFilters> = filtersState.asStateFlow()
    val query: StateFlow<String> = queryState.asStateFlow()

    private val activeFilter = combine(filtersState, queryState.debounce(SEARCH_DEBOUNCE_MS)) { filters, query ->
        filters.toFilter(clock.today(), query)
    }.distinctUntilChanged()

    val accountsList: StateFlow<List<Account>> = accounts.accounts
        .combine(filtersState) { state, _ -> state.dataOrNull.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val payees: StateFlow<List<Payee>> = catalog.payees
        .combine(filtersState) { state, _ -> state.dataOrNull.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val tags: StateFlow<List<Tag>> = catalog.tags
        .combine(filtersState) { state, _ -> state.dataOrNull.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val uiState: StateFlow<FeedUiState> = activeFilter.flatMapLatest { filter ->
        loadPairs(filter.period)
        combine(
            transactions.feed(filter),
            accounts.accounts,
            catalog.categories,
            catalog.tags,
            combine(loadingMore, pairs) { more, found -> more to found },
        ) { feed, accountState, categoryState, tagState, (more, found) ->
            when (feed) {
                is CacheState.Missing -> {
                    val error = feed.error
                    if (error != null && !feed.loading) FeedUiState.Error(error) else FeedUiState.Loading
                }
                else -> {
                    val data = feed.dataOrNull!!
                    val accountList = accountState.dataOrNull.orEmpty()
                    val byAccount = accountList.associateBy { it.id }
                    val debtIds = accountList.filter { it.type == AccountType.DEBT }.map { it.id }.toSet()
                    val categories = categoryState.dataOrNull.orEmpty()
                    val byCategory = categories.flatMap { it.flatten() }.associateBy { it.id }
                    val byTag = tagState.dataOrNull.orEmpty().associateBy { it.id }
                    val pairByExpense = found.associateBy { it.expenseId }
                    val rows = data.transactions.associate { transaction ->
                        transaction.id to FeedRow(
                            transaction = transaction,
                            category = (
                                transaction.categoryId ?: transaction.splits.firstOrNull {
                                    it.categoryId != null
                                }?.categoryId
                                )?.let(byCategory::get),
                            account = byAccount[transaction.accountId],
                            toAccount = transaction.toAccountId?.let(byAccount::get),
                            tags = transaction.tagIds.mapNotNull(byTag::get),
                            debt = debtDirection(transaction, debtIds),
                            pair = pairByExpense[transaction.id],
                        )
                    }
                    FeedUiState.Content(
                        days = groupByDay(data.transactions),
                        rows = rows,
                        totalItems = data.totalItems,
                        hasMore = data.hasMore,
                        loadingMore = more,
                        refreshing = feed.isRefreshing,
                        stale = feed is CacheState.Stale,
                        fetchedAt = (feed as? CacheState.Stale)?.fetchedAt,
                        offline = feed.errorOrNull is ApiError.Network,
                        categories = categories,
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedUiState.Loading)

    init {
        viewModelScope.launch { filtersState.collect { savedState[KEY_FILTERS] = it } }
        viewModelScope.launch { queryState.collect { savedState[KEY_QUERY] = it } }
    }

    fun setFilters(filters: FeedFilters) = filtersState.update { filters }

    fun setPreset(preset: PeriodPreset, custom: Period = filtersState.value.custom) =
        filtersState.update { it.copy(preset = preset, custom = custom) }

    fun setQuery(query: String) = queryState.update { query }

    fun resetFilters() = filtersState.update { FeedFilters(preset = it.preset, custom = it.custom) }

    /** The feed of one account or payee: the filter preset and locked by the screen. */
    fun presetAccount(
        accountId: Long,
    ) = filtersState.update { it.copy(accountId = accountId, preset = PeriodPreset.ALL) }

    fun presetPayee(payeeId: Long) = filtersState.update { it.copy(payeeId = payeeId, preset = PeriodPreset.ALL) }

    /** From the budget: the category's operations of that month. */
    fun presetCategory(categoryId: Long, month: YearMonth) = filtersState.update {
        it.copy(
            categoryId = categoryId,
            preset = PeriodPreset.CUSTOM,
            custom = Period(month.atDay(1), month.atEndOfMonth()),
        )
    }

    fun refresh() = transactions.invalidateAll()

    fun loadMore() {
        val content = uiState.value as? FeedUiState.Content ?: return
        if (!content.hasMore || loadingMore.value) return
        loadingMore.value = true
        val filter = filtersState.value.toFilter(clock.today(), queryState.value)
        val nextPage = content.days.sumOf { it.transactions.size } / TransactionsRepository.PAGE_SIZE
        viewModelScope.launch {
            transactions.loadMore(filter, nextPage)
            loadingMore.value = false
        }
    }

    fun categorize(transaction: Transaction, categoryId: Long, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = transactions.categorize(transaction, categoryId)
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    fun delete(id: Long, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = transactions.delete(id)
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    fun mergePair(pair: TransferPair, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            when (val result = transactions.mergePair(pair)) {
                is ApiResult.Ok -> pairs.update { list -> list - pair }
                is ApiResult.Err -> onError(result.error)
            }
        }
    }

    fun dismissPair(pair: TransferPair, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            when (val result = transactions.dismissPair(pair)) {
                is ApiResult.Ok -> pairs.update { list -> list - pair }
                is ApiResult.Err -> onError(result.error)
            }
        }
    }

    private fun loadPairs(period: Period) {
        viewModelScope.launch {
            pairs.value = (transactions.transferPairs(period) as? ApiResult.Ok)?.value.orEmpty()
        }
    }

    companion object {
        private const val KEY_FILTERS = "filters"
        private const val KEY_QUERY = "query"
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Groups a page (newest first) by date; the day total leaves transfers out, as the web does. */
        fun groupByDay(transactions: List<Transaction>): List<DayGroup> =
            transactions.groupBy { it.date }.map { (date, items) ->
                val totals = items
                    .filter { it.type != TransactionType.TRANSFER && !it.adjustment }
                    .groupBy { it.currency.code }
                    .map { (currency, list) ->
                        currency to list.fold(BigDecimal.ZERO) { sum, t -> sum + t.signedAmount }
                    }
                DayGroup(date, items, totals)
            }
    }
}
