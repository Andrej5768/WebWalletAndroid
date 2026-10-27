package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.Frequency
import org.andrewdev.webwallet.core.model.Named
import org.andrewdev.webwallet.core.model.Occurrence
import org.andrewdev.webwallet.core.model.OccurrenceStatus
import org.andrewdev.webwallet.core.model.Recurring
import org.andrewdev.webwallet.core.model.RecurringDraft
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.RecurringPaymentsApi
import org.andrewdev.webwallet.core.network.generated.model.NamedResponse
import org.andrewdev.webwallet.core.network.generated.model.OccurrenceConfirmRequest
import org.andrewdev.webwallet.core.network.generated.model.OccurrenceLinkRequest
import org.andrewdev.webwallet.core.network.generated.model.OccurrenceResponse
import org.andrewdev.webwallet.core.network.generated.model.RecurringRequest
import org.andrewdev.webwallet.core.network.generated.model.RecurringResponse
import org.andrewdev.webwallet.core.network.map
import org.andrewdev.webwallet.core.network.onOk
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class NamedJson(val id: Long, val name: String)

@Serializable
internal data class RecurringJson(
    val id: Long,
    val name: String,
    val type: String,
    val accountId: Long,
    val amount: String,
    val currency: String,
    val toAccountId: Long?,
    val toAmount: String?,
    val toCurrency: String?,
    val categoryId: Long?,
    val payee: NamedJson?,
    val comment: String?,
    val frequency: String,
    val interval: Int,
    val startDate: String,
    val endDate: String?,
    val autoCreate: Boolean,
    val variableAmount: Boolean,
    val tolerance: Int,
    val nextDate: String?,
)

@Serializable
internal data class RecurringListJson(val items: List<RecurringJson>)

@Serializable
internal data class OccurrenceJson(
    val recurringId: Long,
    val name: String,
    val date: String,
    val status: String,
    val overdue: Boolean,
    val type: String,
    val accountId: Long,
    val amount: String,
    val currency: String,
    val toAccountId: Long?,
    val toAmount: String?,
    val categoryId: Long?,
    val payee: NamedJson?,
    val autoCreate: Boolean,
    val variableAmount: Boolean,
    val transactionId: Long?,
)

@Serializable
internal data class OccurrenceListJson(val items: List<OccurrenceJson>)

/**
 * Recurring payments and their planned occurrences (docs/04 §6.4, §6.1 planned group), cached whole. Confirming,
 * linking or skipping an occurrence changes the feed, so those writes drop the transaction caches too.
 */
@Singleton
class RecurringRepository @Inject constructor(
    private val api: RecurringPaymentsApi,
    private val transactions: TransactionsRepository,
    private val jsonCache: JsonCache,
    private val cache: Cache,
    private val clock: KyivClock,
) {
    val list: Flow<CacheState<List<Recurring>>> = cache.observe(
        key = KEY_LIST,
        ttl = Cache.HOUR,
        local = jsonCache.observe(KEY_LIST, RecurringListJson.serializer()).map { json ->
            json?.items?.map { it.toModel() }
        },
        fetch = {
            apiCall { api.list2() }.map { list ->
                jsonCache.put(KEY_LIST, RecurringListJson.serializer(), RecurringListJson(list.map { it.toJson() }))
            }
        },
    )

    /** The pending dates around today: the overdue ones of the last two months and a month ahead. */
    val occurrences: Flow<CacheState<List<Occurrence>>> = cache.observe(
        key = KEY_OCCURRENCES,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_OCCURRENCES, OccurrenceListJson.serializer()).map { json ->
            json?.items?.map { it.toModel() }
        },
        fetch = {
            val today = clock.today()
            apiCall { api.occurrences(today.minusDays(LOOKBACK_DAYS), today.plusDays(AHEAD_DAYS)) }.map { list ->
                jsonCache.put(
                    KEY_OCCURRENCES,
                    OccurrenceListJson.serializer(),
                    OccurrenceListJson(list.map { it.toJson() }),
                )
            }
        },
    )

    suspend fun get(id: Long): ApiResult<Recurring> = apiCall { api.get1(id) }.map { it.toJson().toModel() }

    /** [key] names the attempt: a retry with the same key gets the first response back instead of a duplicate. */
    suspend fun create(draft: RecurringDraft, key: UUID): ApiResult<Recurring> =
        apiCall { api.create2(draft.toRequest(), key) }.map { it.toJson().toModel() }.onOk { changed() }

    suspend fun update(id: Long, draft: RecurringDraft): ApiResult<Recurring> =
        apiCall { api.update1(id, draft.toRequest()) }.map { it.toJson().toModel() }.onOk { changed() }

    suspend fun delete(id: Long): ApiResult<Unit> = apiCall { api.delete2(id) }.onOk { changed() }

    /** Creates the transaction of the occurrence; the values not given are those of the payment. */
    suspend fun confirm(
        occurrence: Occurrence,
        date: LocalDate? = null,
        amount: BigDecimal? = null,
        toAmount: BigDecimal? = null,
    ): ApiResult<Occurrence> = apiCall {
        api.confirm(occurrence.recurringId, occurrence.date, OccurrenceConfirmRequest(amount, date, toAmount))
    }.map { it.toJson().toModel() }.onOk { paid() }

    suspend fun link(occurrence: Occurrence, transactionId: Long): ApiResult<Occurrence> = apiCall {
        api.link2(occurrence.recurringId, occurrence.date, OccurrenceLinkRequest(transactionId))
    }.map { it.toJson().toModel() }.onOk { paid() }

    suspend fun skip(occurrence: Occurrence): ApiResult<Occurrence> =
        apiCall { api.skip(occurrence.recurringId, occurrence.date) }.map { it.toJson().toModel() }.onOk { changed() }

    /** Transactions that may have paid the occurrence: same type, close in date and amount. */
    suspend fun candidates(occurrence: Occurrence): ApiResult<List<Transaction>> =
        apiCall { api.candidates(occurrence.recurringId, occurrence.date) }
            .map { list -> list.map { it.toEntity().toModel() } }

    fun invalidate() = cache.invalidate(KEY_LIST, KEY_OCCURRENCES)

    private fun changed() = invalidate()

    private fun paid() {
        invalidate()
        transactions.invalidateAll()
    }

    private fun RecurringDraft.toRequest() = RecurringRequest(
        accountId = accountId,
        amount = amount,
        frequency = RecurringRequest.Frequency.valueOf(frequency.name),
        name = name.trim(),
        startDate = startDate,
        type = RecurringRequest.Type.valueOf(type.name),
        autoCreate = autoCreate,
        categoryId = categoryId,
        comment = comment?.trim()?.ifEmpty { null },
        endDate = endDate,
        interval = interval,
        payeeId = payeeId,
        payeeName = payeeName?.trim()?.ifEmpty { null },
        toAccountId = toAccountId,
        toAmount = toAmount,
        tolerance = tolerance,
        variableAmount = variableAmount,
    )

    private fun RecurringResponse.toJson() = RecurringJson(
        id = id,
        name = name,
        type = type.value,
        accountId = accountId,
        amount = amount.toPlainString(),
        currency = currency,
        toAccountId = toAccountId,
        toAmount = toAmount?.toPlainString(),
        toCurrency = toCurrency,
        categoryId = categoryId,
        payee = payee?.toJson(),
        comment = comment,
        frequency = frequency.value,
        interval = interval,
        startDate = startDate.toString(),
        endDate = endDate?.toString(),
        autoCreate = autoCreate,
        variableAmount = variableAmount,
        tolerance = tolerance,
        nextDate = nextDate?.toString(),
    )

    private fun OccurrenceResponse.toJson() = OccurrenceJson(
        recurringId = recurringId,
        name = name,
        date = date.toString(),
        status = status.value,
        overdue = overdue,
        type = type.value,
        accountId = accountId,
        amount = amount.toPlainString(),
        currency = currency,
        toAccountId = toAccountId,
        toAmount = toAmount?.toPlainString(),
        categoryId = categoryId,
        payee = payee?.toJson(),
        autoCreate = autoCreate,
        variableAmount = variableAmount,
        transactionId = transactionId,
    )

    private fun NamedResponse.toJson() = NamedJson(id, name)

    companion object {
        const val KEY_LIST = "recurring"
        const val KEY_OCCURRENCES = "occurrences"
        private const val LOOKBACK_DAYS = 60L
        private const val AHEAD_DAYS = 31L
    }
}

internal fun RecurringJson.toModel() = Recurring(
    id = id,
    name = name,
    type = TransactionType.valueOf(type),
    accountId = accountId,
    amount = BigDecimal(amount),
    currency = CurrencyCode(currency),
    toAccountId = toAccountId,
    toAmount = toAmount?.let(::BigDecimal),
    toCurrency = toCurrency?.let(::CurrencyCode),
    categoryId = categoryId,
    payee = payee?.let { Named(it.id, it.name) },
    comment = comment,
    frequency = Frequency.valueOf(frequency),
    interval = interval,
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let(LocalDate::parse),
    autoCreate = autoCreate,
    variableAmount = variableAmount,
    tolerance = tolerance,
    nextDate = nextDate?.let(LocalDate::parse),
)

internal fun OccurrenceJson.toModel() = Occurrence(
    recurringId = recurringId,
    name = name,
    date = LocalDate.parse(date),
    status = OccurrenceStatus.valueOf(status),
    overdue = overdue,
    type = TransactionType.valueOf(type),
    accountId = accountId,
    amount = BigDecimal(amount),
    currency = CurrencyCode(currency),
    toAccountId = toAccountId,
    toAmount = toAmount?.let(::BigDecimal),
    categoryId = categoryId,
    payee = payee?.let { Named(it.id, it.name) },
    autoCreate = autoCreate,
    variableAmount = variableAmount,
    transactionId = transactionId,
)
