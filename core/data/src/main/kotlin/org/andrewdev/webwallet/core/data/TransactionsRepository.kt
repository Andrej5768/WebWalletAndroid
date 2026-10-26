package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.andrewdev.webwallet.core.database.FeedPageEntity
import org.andrewdev.webwallet.core.database.TransactionDao
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.Split
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.TransactionsApi
import org.andrewdev.webwallet.core.network.generated.model.SplitRequest
import org.andrewdev.webwallet.core.network.generated.model.TransactionRequest
import org.andrewdev.webwallet.core.network.generated.model.TransferPairRequest
import org.andrewdev.webwallet.core.network.map
import org.andrewdev.webwallet.core.network.onOk
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import javax.inject.Inject
import javax.inject.Singleton

/** The filters of the feed (docs/04 §6.1); the key names the cached pages. */
data class FeedFilter(
    val period: Period = Period.ALL,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val payeeId: Long? = null,
    val tagId: Long? = null,
    val type: TransactionType? = null,
    val query: String? = null,
) {
    val key: String
        get() = listOf(
            period.from,
            period.to,
            accountId,
            categoryId,
            payeeId,
            tagId,
            type,
            query.orEmpty(),
        ).joinToString("|")

    /** How many filters besides the period are set, for the badge on the filter icon. */
    val activeCount: Int get() = listOfNotNull(accountId, categoryId, payeeId, tagId, type).size
}

/** The cached pages of one filter set, flattened. */
data class Feed(val transactions: List<Transaction>, val totalItems: Long, val pagesLoaded: Int, val totalPages: Int) {
    val hasMore: Boolean get() = pagesLoaded < totalPages
}

/** A suggested transfer pair: an expense and an income the backend thinks are one transfer. */
data class TransferPair(
    val expenseId: Long,
    val incomeId: Long,
    val date: LocalDate,
    val difference: BigDecimal,
    val exact: Boolean,
)

/** The values of the transaction form, mapped to the request the backend takes. */
data class TransactionDraft(
    val type: TransactionType,
    val date: LocalDate,
    val accountId: Long,
    val amount: BigDecimal,
    val toAccountId: Long? = null,
    val toAmount: BigDecimal? = null,
    val categoryId: Long? = null,
    val payeeId: Long? = null,
    val payeeName: String? = null,
    val comment: String? = null,
    val tagIds: List<Long> = emptyList(),
    val splits: List<Split> = emptyList(),
    val originalAmount: BigDecimal? = null,
    val originalCurrency: String? = null,
    val adjustment: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionsRepository @Inject constructor(
    private val api: TransactionsApi,
    private val dao: TransactionDao,
    private val accounts: AccountsRepository,
    private val catalog: CatalogRepository,
    private val cache: Cache,
) {
    private val idsSerializer = ListSerializer(Long.serializer())

    /** The feed of a filter set: page 0 is refreshed by the policy, more pages are added with [loadMore]. */
    fun feed(filter: FeedFilter): Flow<CacheState<Feed>> = cache.observe(
        key = feedKey(filter),
        ttl = Cache.SHORT,
        local = dao.observePages(filter.key).flatMapLatest { pages -> localFeed(pages) },
        fetch = { loadPage(filter, 0) },
    )

    suspend fun loadMore(filter: FeedFilter, page: Int): ApiResult<Unit> = loadPage(filter, page)

    fun transaction(id: Long): Flow<CacheState<Transaction>> = cache.observe(
        key = "transaction:$id",
        ttl = Cache.SHORT,
        local = dao.observe(id).map { it?.toModel() },
        fetch = { apiCall { api.get(id) }.map { dao.upsert(listOf(it.toEntity())) } },
    )

    suspend fun create(draft: TransactionDraft): ApiResult<Transaction> =
        apiCall { api.create(draft.toRequest()) }.map { it.toEntity().toModel() }.onOk { stored(it) }

    suspend fun update(id: Long, draft: TransactionDraft): ApiResult<Transaction> =
        apiCall { api.update(id, draft.toRequest()) }.map { it.toEntity().toModel() }.onOk { stored(it) }

    /** The dashed-icon action of the feed: the category only, the rest as it is. */
    suspend fun categorize(transaction: Transaction, categoryId: Long): ApiResult<Transaction> =
        update(transaction.id, transaction.toDraft().copy(categoryId = categoryId))

    suspend fun delete(id: Long): ApiResult<Unit> = apiCall { api.delete(id) }.onOk {
        dao.delete(id)
        invalidateAll()
    }

    suspend fun transferPairs(period: Period): ApiResult<List<TransferPair>> {
        val from = period.from ?: LocalDate.of(FIRST_YEAR, 1, 1)
        val to = period.to ?: LocalDate.of(LAST_YEAR, Month.DECEMBER, LAST_DAY_OF_DECEMBER)
        return apiCall { api.suggestions(from, to) }.map { list ->
            list.map { TransferPair(it.expenseId, it.incomeId, it.date, it.difference, it.exact) }
        }
    }

    suspend fun mergePair(pair: TransferPair): ApiResult<Transaction> =
        apiCall { api.merge(TransferPairRequest(pair.expenseId, pair.incomeId)) }
            .map { it.toEntity().toModel() }
            .onOk {
                dao.delete(pair.incomeId)
                stored(it)
            }

    suspend fun dismissPair(pair: TransferPair): ApiResult<Unit> =
        apiCall { api.dismiss(TransferPairRequest(pair.expenseId, pair.incomeId)) }

    /** A merged transfer back into its two bank operations. */
    suspend fun splitTransfer(id: Long): ApiResult<Transaction> =
        apiCall { api.split(id) }.map { it.toEntity().toModel() }.onOk { stored(it) }

    fun invalidateAll() {
        cache.invalidatePrefix(FEED_PREFIX)
        accounts.invalidate()
    }

    private suspend fun stored(transaction: Transaction) {
        dao.upsert(listOf(transaction.toEntity()))
        invalidateAll()
        if (transaction.payee != null) catalog.invalidatePayees()
    }

    private suspend fun loadPage(filter: FeedFilter, page: Int): ApiResult<Unit> = apiCall {
        api.list(
            from = filter.period.from,
            to = filter.period.to,
            accountId = filter.accountId,
            categoryId = filter.categoryId,
            type = filter.type?.let { TransactionsApi.TypeList.valueOf(it.name) },
            payeeId = filter.payeeId,
            tagId = filter.tagId,
            q = filter.query?.ifBlank { null },
            page = page,
            size = PAGE_SIZE,
        )
    }.map { response ->
        dao.upsert(response.items.map { it.toEntity() })
        if (page == 0) dao.dropPagesAfter(filter.key, 0)
        dao.savePage(
            FeedPageEntity(
                filterKey = filter.key,
                page = page,
                transactionIdsJson = json.encodeToString(idsSerializer, response.items.map { it.id }),
                totalItems = response.totalItems,
                totalPages = response.totalPages,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        dao.trimFilters(KEEP_FILTERS)
    }

    private fun localFeed(pages: List<FeedPageEntity>): Flow<Feed?> {
        if (pages.isEmpty()) return flowOf(null)
        val ids = pages.flatMap { json.decodeFromString(idsSerializer, it.transactionIdsJson) }
        val last = pages.last()
        return dao.observeByIds(ids).map { rows ->
            val byId = rows.associateBy { it.id }
            Feed(
                transactions = ids.mapNotNull { byId[it]?.toModel() },
                totalItems = last.totalItems,
                pagesLoaded = pages.size,
                totalPages = last.totalPages,
            )
        }
    }

    private fun feedKey(filter: FeedFilter) = FEED_PREFIX + filter.key

    private fun TransactionDraft.toRequest() = TransactionRequest(
        accountId = accountId,
        amount = amount,
        date = date,
        type = TransactionRequest.Type.valueOf(type.name),
        categoryId = categoryId,
        comment = comment?.ifBlank { null },
        originalAmount = originalAmount,
        originalCurrency = originalCurrency,
        payeeId = payeeId,
        payeeName = payeeName?.ifBlank { null },
        splits = splits.takeIf { it.isNotEmpty() }?.map { SplitRequest(it.amount, it.categoryId, it.comment) },
        tagIds = tagIds,
        toAccountId = toAccountId,
        toAmount = toAmount,
        adjustment = adjustment,
    )

    private fun Transaction.toDraft() = TransactionDraft(
        type = type,
        date = date,
        accountId = accountId,
        amount = amount.abs(),
        toAccountId = toAccountId,
        toAmount = toAmount?.abs(),
        categoryId = if (isSplit) null else categoryId,
        payeeId = payee?.id,
        comment = comment,
        tagIds = tagIds,
        splits = if (isSplit) splits else emptyList(),
        originalAmount = originalAmount,
        originalCurrency = originalCurrency?.code,
        adjustment = adjustment,
    )

    companion object {
        const val PAGE_SIZE = 50
        private const val KEEP_FILTERS = 10
        private const val FEED_PREFIX = "feed:"
        private const val FIRST_YEAR = 1990
        private const val LAST_YEAR = 2100
        private const val LAST_DAY_OF_DECEMBER = 31
    }
}
