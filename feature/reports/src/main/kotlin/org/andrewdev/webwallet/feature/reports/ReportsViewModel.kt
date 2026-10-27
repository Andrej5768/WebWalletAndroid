package org.andrewdev.webwallet.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.data.CacheState
import org.andrewdev.webwallet.core.data.CatalogRepository
import org.andrewdev.webwallet.core.data.ReportsRepository
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.CompareReport
import org.andrewdev.webwallet.core.model.IncomeExpenseReport
import org.andrewdev.webwallet.core.model.NeedsWantsReport
import org.andrewdev.webwallet.core.model.NetWorthReport
import org.andrewdev.webwallet.core.model.PayeesReport
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.PeriodPreset
import org.andrewdev.webwallet.core.model.SavingsReport
import org.andrewdev.webwallet.core.model.SpendingReport
import org.andrewdev.webwallet.core.model.TrendsReport
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class ReportTab { SPENDING, INCOME, NET_WORTH, PAYEES, ANALYTICS }

enum class AnalyticsTab { CASH_FLOW, COMPARE, TRENDS, SAVINGS }

enum class ComparePreset { MOM, YOY, CUSTOM }

/** The two periods of the comparison (web `comparePeriods`, `truncate`, `commonDays`). */
data class ComparePeriods(val fromA: LocalDate, val toA: LocalDate, val fromB: LocalDate, val toB: LocalDate) {
    companion object {
        /**
         * A is the current month, B the month before or the same month a year ago; with [sameDate] both end on
         * today's day of the month, so a half-gone month is not compared with a whole one.
         */
        fun of(preset: ComparePreset, today: LocalDate, sameDate: Boolean, custom: ComparePeriods?): ComparePeriods {
            if (preset == ComparePreset.CUSTOM) {
                val periods = custom ?: of(ComparePreset.MOM, today, sameDate = false, custom = null)
                return if (sameDate) periods.truncate(periods.commonDays()) else periods
            }
            val startA = today.withDayOfMonth(1)
            val startB = if (preset == ComparePreset.MOM) startA.minusMonths(1) else startA.minusYears(1)
            val whole = ComparePeriods(
                startA,
                startA.withDayOfMonth(startA.lengthOfMonth()),
                startB,
                startB.withDayOfMonth(startB.lengthOfMonth()),
            )
            return if (sameDate) whole.truncate(today.dayOfMonth) else whole
        }
    }

    /** Both periods cut to their first [days] days; a period never grows. */
    fun truncate(days: Int): ComparePeriods {
        fun cut(from: LocalDate, to: LocalDate): LocalDate = minOf(from.plusDays(days - 1L), to)
        return copy(toA = cut(fromA, toA), toB = cut(fromB, toB))
    }

    /** Days of the shorter period. */
    fun commonDays(): Int = minOf(ChronoUnit.DAYS.between(fromA, toA), ChronoUnit.DAYS.between(fromB, toB)).toInt() + 1
}

data class ReportsUiState(
    val tab: ReportTab = ReportTab.SPENDING,
    val preset: PeriodPreset = PeriodPreset.TWELVE_MONTHS,
    val custom: Period = Period.ALL,
    val period: Period = Period.ALL,
    val categoriesById: Map<Long, Category> = emptyMap(),
    val spending: CacheState<SpendingReport> = CacheState.Missing(loading = true, error = null),
    val incomeExpense: CacheState<IncomeExpenseReport> = CacheState.Missing(loading = true, error = null),
    val netWorth: CacheState<NetWorthReport> = CacheState.Missing(loading = true, error = null),
    val payees: CacheState<PayeesReport> = CacheState.Missing(loading = true, error = null),
)

data class AnalyticsUiState(
    val tab: AnalyticsTab = AnalyticsTab.CASH_FLOW,
    val comparePreset: ComparePreset = ComparePreset.MOM,
    val sameDate: Boolean = true,
    val customPeriods: ComparePeriods? = null,
    val periods: ComparePeriods? = null,
    /** Categories chosen for the trend lines, by id (null is "uncategorized"); at most five. */
    val trendSelection: Set<Long?> = emptySet(),
    val savingsTarget: BigDecimal = DEFAULT_TARGET,
    val cashFlow: CacheState<IncomeExpenseReport> = CacheState.Missing(loading = true, error = null),
    val compare: CacheState<CompareReport> = CacheState.Missing(loading = true, error = null),
    val trends: CacheState<TrendsReport> = CacheState.Missing(loading = true, error = null),
    val savings: CacheState<SavingsReport> = CacheState.Missing(loading = true, error = null),
    val needsWants: CacheState<NeedsWantsReport> = CacheState.Missing(loading = true, error = null),
) {
    companion object {
        val DEFAULT_TARGET: BigDecimal = BigDecimal("0.2")
    }
}

/** The reports (docs/04 §9): a period for the four reports, and the analytics with their own ranges. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reports: ReportsRepository,
    catalog: CatalogRepository,
    private val clock: KyivClock,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val tab = MutableStateFlow(savedState.get<String>(KEY_TAB)?.let(ReportTab::valueOf) ?: ReportTab.SPENDING)
    private val preset = MutableStateFlow(
        savedState.get<String>(KEY_PRESET)?.let(PeriodPreset::valueOf) ?: PeriodPreset.TWELVE_MONTHS,
    )
    private val custom = MutableStateFlow(Period.ALL)
    private val analytics = MutableStateFlow(AnalyticsUiState())

    private val period: Flow<Period> = combine(preset, custom) { p, c -> Period.of(p, clock.today(), c) }

    val uiState: StateFlow<ReportsUiState> = combine(
        combine(
            tab,
            preset,
            custom,
            period,
        ) { t, p, c, range -> ReportsUiState(tab = t, preset = p, custom = c, period = range) },
        catalog.categories,
        period.flatMapLatest { range ->
            combine(
                reports.spending(range),
                reports.incomeExpense(range),
                reports.netWorth(range),
                reports.payees(range),
            ) { s, i, n, p -> Reports(s, i, n, p) }
        },
    ) { base, categories, data ->
        base.copy(
            categoriesById = categories.dataOrNull.orEmpty().flatMap { it.flatten() }.associateBy { it.id },
            spending = data.spending,
            incomeExpense = data.incomeExpense,
            netWorth = data.netWorth,
            payees = data.payees,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReportsUiState())

    val analyticsState: StateFlow<AnalyticsUiState> = analytics.flatMapLatest { state ->
        val today = clock.today()
        val periods = ComparePeriods.of(state.comparePreset, today, state.sameDate, state.customPeriods)
        val year = Period.of(PeriodPreset.TWELVE_MONTHS, today)
        val from = YearMonth.from(year.from!!).toString()
        val to = YearMonth.from(today).toString()
        val lastMonth = YearMonth.from(today).minusMonths(1)
        combine(
            reports.incomeExpense(year),
            reports.compare(periods.fromA, periods.toA, periods.fromB, periods.toB),
            reports.trends(from, to),
            reports.savings(from, to, state.savingsTarget),
            reports.needsWants(Period(lastMonth.atDay(1), lastMonth.atEndOfMonth())),
        ) { cashFlow, compare, trends, savings, needsWants ->
            state.copy(
                periods = periods,
                cashFlow = cashFlow,
                compare = compare,
                trends = trends,
                savings = savings,
                needsWants = needsWants,
                trendSelection = state.trendSelection.ifEmpty {
                    trends.dataOrNull?.categories?.take(DEFAULT_TREND_LINES)?.map { it.categoryId }?.toSet().orEmpty()
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AnalyticsUiState())

    fun setTab(value: ReportTab) {
        savedState[KEY_TAB] = value.name
        tab.value = value
    }

    fun setPreset(value: PeriodPreset, range: Period = custom.value) {
        savedState[KEY_PRESET] = value.name
        custom.value = range
        preset.value = value
    }

    fun refresh() = reports.invalidateAll()

    fun setAnalyticsTab(value: AnalyticsTab) = analytics.update { it.copy(tab = value) }

    fun setComparePreset(value: ComparePreset) = analytics.update { it.copy(comparePreset = value) }

    fun setSameDate(value: Boolean) = analytics.update { it.copy(sameDate = value) }

    fun setCustomPeriods(value: ComparePeriods) =
        analytics.update { it.copy(customPeriods = value, comparePreset = ComparePreset.CUSTOM) }

    /** Toggles a category line; at most five are shown at once. */
    fun toggleTrend(categoryId: Long?) = analytics.update {
        val selected = categoryId in it.trendSelection
        val next = if (selected) it.trendSelection - categoryId else it.trendSelection + categoryId
        if (next.size > MAX_TREND_LINES) it else it.copy(trendSelection = next)
    }

    fun setSavingsTarget(value: BigDecimal) = analytics.update { it.copy(savingsTarget = value) }

    private class Reports(
        val spending: CacheState<SpendingReport>,
        val incomeExpense: CacheState<IncomeExpenseReport>,
        val netWorth: CacheState<NetWorthReport>,
        val payees: CacheState<PayeesReport>,
    )

    companion object {
        private const val KEY_TAB = "tab"
        private const val KEY_PRESET = "preset"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_TREND_LINES = 3
        const val MAX_TREND_LINES = 5
    }
}

/** Where the reports send the user. */
class ReportsActions(
    val openCategory: (id: Long, name: String, month: YearMonth) -> Unit,
    val openPayee: (id: Long, name: String) -> Unit,
    val back: () -> Unit,
)
