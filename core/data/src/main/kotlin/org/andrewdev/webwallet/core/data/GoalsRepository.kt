package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.Goal
import org.andrewdev.webwallet.core.model.GoalDraft
import org.andrewdev.webwallet.core.model.GoalStatus
import org.andrewdev.webwallet.core.model.GoalsSummary
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.PeriodPreset
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.GoalsApi
import org.andrewdev.webwallet.core.network.generated.api.ReportsApi
import org.andrewdev.webwallet.core.network.generated.model.ContributionRequest
import org.andrewdev.webwallet.core.network.generated.model.GoalRequest
import org.andrewdev.webwallet.core.network.generated.model.GoalResponse
import org.andrewdev.webwallet.core.network.map
import org.andrewdev.webwallet.core.network.onOk
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class GoalJson(
    val id: Long,
    val name: String,
    val targetAmount: String,
    val currency: String,
    val targetDate: String?,
    val accountId: Long?,
    val saved: String,
    val remaining: String,
    val progress: String,
    val startDate: String,
    val startAmount: String,
    val expected: String?,
    val monthsLeft: Int?,
    val monthlyNeeded: String?,
    val status: String,
    val achievedOn: String?,
    val icon: String?,
    val color: String?,
    val sortOrder: Int,
)

@Serializable
internal data class GoalsJson(
    val currency: String,
    val saved: String,
    val target: String,
    val monthlyNeeded: String,
    val goals: List<GoalJson>,
    val unconverted: List<String>,
)

/** One month of `GET /reports/income-expense`, for the averages behind FIRE and FYM. */
data class MonthIncomeExpense(val month: String, val income: BigDecimal, val expense: BigDecimal)

@Serializable
internal data class MonthsJson(val currency: String, val months: List<MonthJson>)

@Serializable
internal data class MonthJson(val month: String, val income: String, val expense: String)

/** The savings goals (docs/04 §10), cached whole, and the last twelve months of income and expenses. */
@Singleton
class GoalsRepository @Inject constructor(
    private val api: GoalsApi,
    private val reportsApi: ReportsApi,
    private val accounts: AccountsRepository,
    private val jsonCache: JsonCache,
    private val cache: Cache,
    private val clock: KyivClock,
) {
    val goals: Flow<CacheState<GoalsSummary>> = cache.observe(
        key = KEY_GOALS,
        ttl = Cache.HOUR,
        local = jsonCache.observe(KEY_GOALS, GoalsJson.serializer()).map { it?.toModel() },
        fetch = {
            apiCall { api.list4() }.map { response ->
                jsonCache.put(
                    KEY_GOALS,
                    GoalsJson.serializer(),
                    GoalsJson(
                        response.currency,
                        response.saved.toPlainString(),
                        response.target.toPlainString(),
                        response.monthlyNeeded.toPlainString(),
                        response.goals.map { it.toJson() },
                        response.unconvertedCurrencies,
                    ),
                )
            }
        },
    )

    /** Income and expenses of the last twelve months in the base currency, with the currency first. */
    val twelveMonths: Flow<CacheState<Pair<CurrencyCode, List<MonthIncomeExpense>>>> = cache.observe(
        key = KEY_MONTHS,
        ttl = Cache.HOUR,
        local = jsonCache.observe(KEY_MONTHS, MonthsJson.serializer()).map { json ->
            json?.let { CurrencyCode(it.currency) to it.months.map { m -> m.toModel() } }
        },
        fetch = {
            val period = Period.of(PeriodPreset.TWELVE_MONTHS, clock.today())
            apiCall { reportsApi.incomeExpense(period.from, period.to) }.map { response ->
                jsonCache.put(
                    KEY_MONTHS,
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

    /** [key] names the attempt: a retry with the same key gets the first response back instead of a duplicate. */
    suspend fun create(draft: GoalDraft, key: UUID): ApiResult<Goal> =
        apiCall { api.create5(draft.toRequest(), key) }.map { it.toJson().toModel() }.onOk { changed() }

    suspend fun update(id: Long, draft: GoalDraft): ApiResult<Goal> =
        apiCall { api.update4(id, draft.toRequest()) }.map { it.toJson().toModel() }.onOk { changed() }

    suspend fun delete(id: Long): ApiResult<Unit> = apiCall { api.delete5(id) }.onOk { changed() }

    /** Adds to (or, negative, takes from) the progress of a goal tracked by hand. */
    suspend fun contribute(id: Long, amount: BigDecimal, key: UUID): ApiResult<Goal> =
        apiCall {
            api.contribute(
                id,
                ContributionRequest(amount),
                key,
            )
        }.map { it.toJson().toModel() }.onOk { changed() }

    /** Marks the goal as achieved or active again; the other fields stay as they are. */
    suspend fun setAchieved(goal: Goal, achieved: Boolean): ApiResult<Goal> = update(goal.id, goal.toDraft(achieved))

    fun invalidate() = cache.invalidate(KEY_GOALS, KEY_MONTHS)

    private fun changed() {
        cache.invalidate(KEY_GOALS)
        accounts.invalidate()
    }

    private fun Goal.toDraft(achieved: Boolean) = GoalDraft(
        name = name,
        targetAmount = targetAmount,
        currency = currency.code,
        targetDate = targetDate,
        accountId = accountId,
        saved = if (accountId == null) saved else null,
        icon = icon,
        color = color,
        achieved = achieved,
        sortOrder = sortOrder,
    )

    private fun GoalDraft.toRequest() = GoalRequest(
        name = name.trim(),
        targetAmount = targetAmount,
        accountId = accountId,
        achieved = achieved,
        color = color?.let { c -> GoalRequest.Color.entries.firstOrNull { it.value == c } },
        currency = currency,
        icon = icon,
        saved = saved,
        sortOrder = sortOrder,
        targetDate = targetDate,
    )

    private fun GoalResponse.toJson() = GoalJson(
        id = id,
        name = name,
        targetAmount = targetAmount.toPlainString(),
        currency = currency,
        targetDate = targetDate?.toString(),
        accountId = accountId,
        saved = saved.toPlainString(),
        remaining = remaining.toPlainString(),
        progress = progress.toPlainString(),
        startDate = startDate.toString(),
        startAmount = startAmount.toPlainString(),
        expected = expected?.toPlainString(),
        monthsLeft = monthsLeft,
        monthlyNeeded = monthlyNeeded?.toPlainString(),
        status = status.value,
        achievedOn = achievedOn?.toString(),
        icon = icon,
        color = color?.value,
        sortOrder = sortOrder,
    )

    companion object {
        const val KEY_GOALS = "goals"
        const val KEY_MONTHS = "reports:income-expense:12m"
    }
}

internal fun GoalsJson.toModel() = GoalsSummary(
    currency = CurrencyCode(currency),
    saved = BigDecimal(saved),
    target = BigDecimal(target),
    monthlyNeeded = BigDecimal(monthlyNeeded),
    goals = goals.map { it.toModel() },
    unconvertedCurrencies = unconverted,
)

internal fun GoalJson.toModel() = Goal(
    id = id,
    name = name,
    targetAmount = BigDecimal(targetAmount),
    currency = CurrencyCode(currency),
    targetDate = targetDate?.let(LocalDate::parse),
    accountId = accountId,
    saved = BigDecimal(saved),
    remaining = BigDecimal(remaining),
    progress = BigDecimal(progress),
    startDate = LocalDate.parse(startDate),
    startAmount = BigDecimal(startAmount),
    expected = expected?.let(::BigDecimal),
    monthsLeft = monthsLeft,
    monthlyNeeded = monthlyNeeded?.let(::BigDecimal),
    status = GoalStatus.valueOf(status),
    achievedOn = achievedOn?.let(LocalDate::parse),
    icon = icon,
    color = color,
    sortOrder = sortOrder,
)

internal fun MonthJson.toModel() = MonthIncomeExpense(month, BigDecimal(income), BigDecimal(expense))
