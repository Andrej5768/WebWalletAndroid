package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.DebtPerson
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.PeriodPreset
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.DebtsApi
import org.andrewdev.webwallet.core.network.generated.api.MonobankApi
import org.andrewdev.webwallet.core.network.generated.api.RecurringPaymentsApi
import org.andrewdev.webwallet.core.network.generated.api.ReportsApi
import org.andrewdev.webwallet.core.network.map
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** Income, expenses and the savings rate of the current month, in the base currency. */
data class MonthFigures(
    val currency: CurrencyCode,
    val income: BigDecimal,
    val expense: BigDecimal,
    val savingsRate: BigDecimal?,
    val unconvertedCurrencies: List<String>,
)

/** A Monobank-linked account whose balance differs from the bank's. */
data class BankDifference(
    val accountId: Long,
    val accountName: String,
    val difference: BigDecimal,
    val currency: CurrencyCode,
)

/** What the Monobank card of the overview and the accounts screen need: the linked accounts and their state. */
data class MonobankStatus(
    val connected: Boolean,
    val lastError: String?,
    val differences: List<BankDifference>,
    val linkedAccountIds: Set<Long>,
)

@Serializable
internal data class MonthFiguresJson(
    val currency: String,
    val income: String,
    val expense: String,
    val savingsRate: String?,
    val unconverted: List<String>,
)

@Serializable
internal data class MonobankJson(
    val connected: Boolean,
    val lastError: String?,
    val differences: List<DifferenceJson>,
    val linked: List<Long>,
)

@Serializable
internal data class DifferenceJson(
    val accountId: Long,
    val accountName: String,
    val difference: String,
    val currency: String,
)

/** The figures of the overview that are not accounts or transactions (docs/04 §4, M1 part), cached whole. */
@Singleton
class OverviewRepository @Inject constructor(
    private val reportsApi: ReportsApi,
    private val recurringApi: RecurringPaymentsApi,
    private val monobankApi: MonobankApi,
    private val debtsApi: DebtsApi,
    private val jsonCache: JsonCache,
    private val cache: Cache,
    private val clock: KyivClock,
) {
    val monthFigures: Flow<CacheState<MonthFigures>> = cache.observe(
        key = KEY_MONTH,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_MONTH, MonthFiguresJson.serializer()).map { it?.toModel() },
        fetch = {
            val month = Period.of(PeriodPreset.MONTH, clock.today())
            apiCall { reportsApi.incomeExpense(month.from, month.to) }.map {
                jsonCache.put(
                    KEY_MONTH,
                    MonthFiguresJson.serializer(),
                    MonthFiguresJson(
                        it.currency,
                        it.income.toPlainString(),
                        it.expense.toPlainString(),
                        it.savingsRate?.toPlainString(),
                        it.unconvertedCurrencies,
                    ),
                )
            }
        },
    )

    val monobank: Flow<CacheState<MonobankStatus>> = cache.observe(
        key = KEY_MONOBANK,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_MONOBANK, MonobankJson.serializer()).map { it?.toModel() },
        fetch = ::fetchMonobank,
    )

    val debts: Flow<CacheState<List<DebtPerson>>> = cache.observe(
        key = KEY_DEBTS,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_DEBTS, DebtsJson.serializer()).map { it?.toModel() },
        fetch = {
            apiCall { debtsApi.debts() }.map {
                jsonCache.put(
                    KEY_DEBTS,
                    DebtsJson.serializer(),
                    DebtsJson(it.accountId, it.currency, it.people.map { p -> p.toJson() }),
                )
            }
        },
    )

    /** How many planned payments are overdue today; the overview shows the alert when above zero. */
    suspend fun overdueCount(): ApiResult<Int> {
        val today = clock.today()
        return apiCall { recurringApi.occurrences(today.minusDays(OVERDUE_LOOKBACK_DAYS), today) }
            .map { list -> list.count { it.overdue } }
    }

    suspend fun syncMonobankNow(): ApiResult<Unit> = apiCall { monobankApi.syncNow() }.map {
        cache.invalidate(
            KEY_MONOBANK,
        )
    }

    fun invalidate() = cache.invalidate(KEY_MONTH, KEY_MONOBANK, KEY_DEBTS)

    private suspend fun fetchMonobank(): ApiResult<Unit> {
        val result = apiCall { monobankApi.status() }
        return when (result) {
            is ApiResult.Ok -> {
                val status = result.value
                jsonCache.put(
                    KEY_MONOBANK,
                    MonobankJson.serializer(),
                    MonobankJson(
                        connected = true,
                        lastError = status.lastError,
                        differences = status.accounts.mapNotNull { link ->
                            val difference = link.difference ?: return@mapNotNull null
                            val accountId = link.accountId ?: return@mapNotNull null
                            if (difference.signum() == 0) return@mapNotNull null
                            DifferenceJson(
                                accountId,
                                link.accountName ?: link.title,
                                difference.toPlainString(),
                                link.currency,
                            )
                        },
                        linked = status.accounts.mapNotNull { it.accountId },
                    ),
                )
                ApiResult.Ok(Unit)
            }
            is ApiResult.Err -> if (result.error.isNotFound()) {
                jsonCache.put(
                    KEY_MONOBANK,
                    MonobankJson.serializer(),
                    MonobankJson(false, null, emptyList(), emptyList()),
                )
                ApiResult.Ok(Unit)
            } else {
                result
            }
        }
    }

    private fun ApiError.isNotFound() = this is ApiError.Problem && status == HTTP_NOT_FOUND

    private fun MonthFiguresJson.toModel() =
        MonthFigures(
            CurrencyCode(currency),
            BigDecimal(income),
            BigDecimal(expense),
            savingsRate?.let(::BigDecimal),
            unconverted,
        )

    private fun MonobankJson.toModel() = MonobankStatus(
        connected = connected,
        lastError = lastError,
        differences = differences.map {
            BankDifference(
                it.accountId,
                it.accountName,
                BigDecimal(it.difference),
                CurrencyCode(it.currency),
            )
        },
        linkedAccountIds = linked.toSet(),
    )

    companion object {
        const val KEY_MONTH = "overview:month"
        const val KEY_MONOBANK = "monobank"
        const val KEY_DEBTS = "debts"
        private const val OVERDUE_LOOKBACK_DAYS = 60L
        private const val HTTP_NOT_FOUND = 404
    }
}
