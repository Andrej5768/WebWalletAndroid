package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.PeriodPreset
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.ForecastApi
import org.andrewdev.webwallet.core.network.generated.api.ReportsApi
import org.andrewdev.webwallet.core.network.map
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** `GET /forecast`: the balance of cards and cash to the end of the month (docs/04 §4 item 4). */
data class Forecast(
    val currency: CurrencyCode,
    val today: LocalDate,
    val balance: BigDecimal,
    val dailySpending: BigDecimal,
    val days: List<ForecastDay>,
    val events: List<ForecastEvent>,
    val lowest: ForecastDay?,
    val unconvertedCurrencies: List<String>,
)

data class ForecastDay(val date: LocalDate, val balance: BigDecimal, val actual: Boolean, val operations: Int)

/** A pending recurring payment that changes the forecast; an overdue one is expected tomorrow. */
data class ForecastEvent(
    val recurringId: Long,
    val name: String,
    val date: LocalDate,
    val amount: BigDecimal,
    val categoryId: Long?,
    val overdue: Boolean,
)

/** A parent category of `GET /reports/spending` for the month. */
data class SpendingRow(val categoryId: Long, val name: String, val amount: BigDecimal, val share: BigDecimal)

data class MonthSpending(val currency: CurrencyCode, val total: BigDecimal, val rows: List<SpendingRow>)

@Serializable
internal data class ForecastJson(
    val currency: String,
    val today: String,
    val balance: String,
    val dailySpending: String,
    val days: List<ForecastDayJson>,
    val events: List<ForecastEventJson>,
    val lowest: ForecastDayJson?,
    val unconverted: List<String>,
)

@Serializable
internal data class ForecastDayJson(val date: String, val balance: String, val actual: Boolean, val operations: Int)

@Serializable
internal data class ForecastEventJson(
    val recurringId: Long,
    val name: String,
    val date: String,
    val amount: String,
    val categoryId: Long?,
    val overdue: Boolean,
)

@Serializable
internal data class SpendingJson(val currency: String, val total: String, val rows: List<SpendingRowJson>)

@Serializable
internal data class SpendingRowJson(val categoryId: Long, val name: String, val amount: String, val share: String)

/** The charts of the overview (docs/04 §4, M2 part): the forecast, the top categories and the last six months. */
@Singleton
class OverviewChartsRepository @Inject constructor(
    private val forecastApi: ForecastApi,
    private val reportsApi: ReportsApi,
    private val jsonCache: JsonCache,
    private val cache: Cache,
    private val clock: KyivClock,
) {
    val forecast: Flow<CacheState<Forecast>> = cache.observe(
        key = KEY_FORECAST,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_FORECAST, ForecastJson.serializer()).map { it?.toModel() },
        fetch = {
            val today = clock.today()
            apiCall { forecastApi.forecast(today.withDayOfMonth(today.lengthOfMonth())) }.map { response ->
                jsonCache.put(
                    KEY_FORECAST,
                    ForecastJson.serializer(),
                    ForecastJson(
                        currency = response.currency,
                        today = response.today.toString(),
                        balance = response.balance.toPlainString(),
                        dailySpending = response.dailySpending.toPlainString(),
                        days = response.days.map {
                            ForecastDayJson(
                                it.date.toString(),
                                it.balance.toPlainString(),
                                it.actual,
                                it.operations.size,
                            )
                        },
                        events = response.events.map {
                            ForecastEventJson(
                                it.recurringId,
                                it.name,
                                it.date.toString(),
                                it.amount.toPlainString(),
                                it.categoryId,
                                it.overdue,
                            )
                        },
                        lowest = response.lowest?.let {
                            ForecastDayJson(
                                it.date.toString(),
                                it.balance.toPlainString(),
                                it.actual,
                                it.operations.size,
                            )
                        },
                        unconverted = response.unconvertedCurrencies,
                    ),
                )
            }
        },
    )

    /** The spending of the current month by parent category, the biggest first. */
    val monthSpending: Flow<CacheState<MonthSpending>> = cache.observe(
        key = KEY_SPENDING,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_SPENDING, SpendingJson.serializer()).map { it?.toModel() },
        fetch = {
            val month = Period.of(PeriodPreset.MONTH, clock.today())
            apiCall { reportsApi.spending(month.from, month.to) }.map { response ->
                jsonCache.put(
                    KEY_SPENDING,
                    SpendingJson.serializer(),
                    SpendingJson(
                        response.currency,
                        response.total.toPlainString(),
                        response.categories.map {
                            SpendingRowJson(it.categoryId, it.name, it.amount.toPlainString(), it.share.toPlainString())
                        },
                    ),
                )
            }
        },
    )

    /** Income and expenses of the last six months. */
    val sixMonths: Flow<CacheState<Pair<CurrencyCode, List<MonthIncomeExpense>>>> = cache.observe(
        key = KEY_SIX_MONTHS,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_SIX_MONTHS, MonthsJson.serializer()).map { json ->
            json?.let { CurrencyCode(it.currency) to it.months.map { m -> m.toModel() } }
        },
        fetch = {
            val today = clock.today()
            val from = today.minusMonths(FIVE_MONTHS).withDayOfMonth(1)
            apiCall { reportsApi.incomeExpense(from, today) }.map { response ->
                jsonCache.put(
                    KEY_SIX_MONTHS,
                    MonthsJson.serializer(),
                    MonthsJson(
                        response.currency,
                        response.months.map {
                            MonthJson(it.month, it.income.toPlainString(), it.expense.toPlainString())
                        },
                    ),
                )
            }
        },
    )

    fun invalidate() = cache.invalidate(KEY_FORECAST, KEY_SPENDING, KEY_SIX_MONTHS)

    companion object {
        const val KEY_FORECAST = "overview:forecast"
        const val KEY_SPENDING = "overview:spending"
        const val KEY_SIX_MONTHS = "overview:six-months"
        private const val FIVE_MONTHS = 5L
    }
}

internal fun ForecastJson.toModel() = Forecast(
    currency = CurrencyCode(currency),
    today = LocalDate.parse(today),
    balance = BigDecimal(balance),
    dailySpending = BigDecimal(dailySpending),
    days = days.map { it.toModel() },
    events = events.map {
        ForecastEvent(
            it.recurringId,
            it.name,
            LocalDate.parse(it.date),
            BigDecimal(it.amount),
            it.categoryId,
            it.overdue,
        )
    },
    lowest = lowest?.toModel(),
    unconvertedCurrencies = unconverted,
)

internal fun ForecastDayJson.toModel() = ForecastDay(LocalDate.parse(date), BigDecimal(balance), actual, operations)

internal fun SpendingJson.toModel() = MonthSpending(
    CurrencyCode(currency),
    BigDecimal(total),
    rows.map { SpendingRow(it.categoryId, it.name, BigDecimal(it.amount), BigDecimal(it.share)) },
)
