package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.andrewdev.webwallet.core.model.BudgetMonth
import org.andrewdev.webwallet.core.model.BudgetRow
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.BudgetApi
import org.andrewdev.webwallet.core.network.generated.model.AssignRequest
import org.andrewdev.webwallet.core.network.generated.model.CategoryRow
import org.andrewdev.webwallet.core.network.generated.model.MonthResponse
import org.andrewdev.webwallet.core.network.generated.model.MoveRequest
import org.andrewdev.webwallet.core.network.map
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class BudgetMonthJson(
    val month: String,
    val currency: String,
    val readyToAssign: String,
    val inflow: String,
    val assigned: String,
    val activity: String,
    val available: String,
    val rows: List<BudgetRowJson>,
    val unconverted: List<String>,
)

@Serializable
internal data class BudgetRowJson(
    val categoryId: Long,
    val name: String,
    val parentId: Long?,
    val archived: Boolean,
    val assigned: String,
    val activity: String,
    val available: String,
    val ownAssigned: String,
    val ownActivity: String,
    val ownAvailable: String,
    val children: List<BudgetRowJson>,
)

/** The budget of a month (docs/04 §7), cached whole per month; every write stores the month the server returns. */
@Singleton
class BudgetRepository @Inject constructor(
    private val api: BudgetApi,
    private val jsonCache: JsonCache,
    private val cache: Cache,
) {
    fun month(month: YearMonth): Flow<CacheState<BudgetMonth>> = cache.observe(
        key = key(month),
        ttl = Cache.SHORT,
        local = jsonCache.observe(key(month), BudgetMonthJson.serializer()).map { it?.toModel() },
        fetch = { apiCall { api.month(month.toString()) }.map { store(it) } },
    )

    /** Sets the own plan of a category; 0 removes it. */
    suspend fun assign(month: YearMonth, categoryId: Long, assigned: BigDecimal): ApiResult<BudgetMonth> =
        apiCall { api.assign(month.toString(), categoryId, AssignRequest(assigned)) }.map { stored(it) }

    suspend fun move(month: YearMonth, from: Long, to: Long, amount: BigDecimal): ApiResult<BudgetMonth> =
        apiCall { api.move(month.toString(), MoveRequest(amount, from, to)) }.map { stored(it) }

    /** Gives every category without a plan this month the plan it had the month before. */
    suspend fun copyPrevious(month: YearMonth): ApiResult<BudgetMonth> =
        apiCall { api.copyPrevious(month.toString()) }.map { stored(it) }

    /** The own plan of the category in the month before, for the "same as last month" shortcut. */
    suspend fun previousAssigned(month: YearMonth, categoryId: Long): ApiResult<BigDecimal> {
        val previous = month.minusMonths(1)
        return apiCall { api.month(previous.toString()) }.map { response ->
            stored(response).flatten().firstOrNull { it.categoryId == categoryId }?.ownAssigned ?: BigDecimal.ZERO
        }
    }

    fun invalidate(month: YearMonth) = cache.invalidate(key(month))

    /** After a write: the server's month replaces the cached one and counts as fresh. */
    private suspend fun stored(response: MonthResponse): BudgetMonth {
        cache.markFresh(key(YearMonth.parse(response.month)))
        store(response)
        return response.toJson().toModel()
    }

    private suspend fun store(response: MonthResponse) {
        jsonCache.put(key(YearMonth.parse(response.month)), BudgetMonthJson.serializer(), response.toJson())
    }

    private fun key(month: YearMonth) = PREFIX + month

    private fun MonthResponse.toJson() = BudgetMonthJson(
        month = month,
        currency = currency,
        readyToAssign = readyToAssign.toPlainString(),
        inflow = inflow.toPlainString(),
        assigned = assigned.toPlainString(),
        activity = activity.toPlainString(),
        available = available.toPlainString(),
        rows = categories.map { it.toJson() },
        unconverted = unconvertedCurrencies,
    )

    private fun CategoryRow.toJson(): BudgetRowJson = BudgetRowJson(
        categoryId = categoryId,
        name = name,
        parentId = parentId,
        archived = archived,
        assigned = assigned.toPlainString(),
        activity = activity.toPlainString(),
        available = available.toPlainString(),
        ownAssigned = ownAssigned.toPlainString(),
        ownActivity = ownActivity.toPlainString(),
        ownAvailable = ownAvailable.toPlainString(),
        children = children.map { it.toJson() },
    )

    companion object {
        /** Every cached month starts with it, so a transaction write can drop them all. */
        const val PREFIX = "budget:"
    }
}

internal fun BudgetMonthJson.toModel() = BudgetMonth(
    month = YearMonth.parse(month),
    currency = CurrencyCode(currency),
    readyToAssign = BigDecimal(readyToAssign),
    inflow = BigDecimal(inflow),
    assigned = BigDecimal(assigned),
    activity = BigDecimal(activity),
    available = BigDecimal(available),
    rows = rows.map { it.toModel() },
    unconvertedCurrencies = unconverted,
)

internal fun BudgetRowJson.toModel(): BudgetRow = BudgetRow(
    categoryId = categoryId,
    name = name,
    parentId = parentId,
    archived = archived,
    assigned = BigDecimal(assigned),
    activity = BigDecimal(activity),
    available = BigDecimal(available),
    ownAssigned = BigDecimal(ownAssigned),
    ownActivity = BigDecimal(ownActivity),
    ownAvailable = BigDecimal(ownAvailable),
    children = children.map { it.toModel() },
)
