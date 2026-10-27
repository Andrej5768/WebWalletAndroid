package org.andrewdev.webwallet.feature.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.data.BudgetRepository
import org.andrewdev.webwallet.core.data.CacheState
import org.andrewdev.webwallet.core.data.CatalogRepository
import org.andrewdev.webwallet.core.model.BudgetMonth
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject

sealed interface BudgetUiState {
    data class Loading(override val month: YearMonth) : BudgetUiState

    data class Error(override val month: YearMonth, val error: ApiError) : BudgetUiState

    data class Content(
        override val month: YearMonth,
        val data: BudgetMonth,
        val groups: List<EnvelopeGroup>,
        val overspent: List<Envelope>,
        /** Share of the month that has passed, for the "today" marker; null for other months. */
        val progress: Float?,
        val categoriesById: Map<Long, Category>,
        /** Categories whose plan is being saved. */
        val pending: Set<Long>,
        val stale: Boolean,
        val fetchedAt: Instant?,
        val offline: Boolean,
        val refreshing: Boolean,
        /** A move or a copy is running. */
        val busy: Boolean,
    ) : BudgetUiState {
        /** Nothing planned or spent: the screen offers to take last month's plan. */
        val isEmpty: Boolean
            get() = !data.hasMoney && groups.flatMap { it.envelopes }.none { it.assigned.signum() != 0 }
    }

    val month: YearMonth
}

/** The budget tab (docs/04 §7): one month at a time, plans saved optimistically, moves and the copy of last month. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budget: BudgetRepository,
    catalog: CatalogRepository,
    private val clock: KyivClock,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val monthState =
        MutableStateFlow(savedState.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: clock.thisMonth())
    private val pending = MutableStateFlow<Map<Long, BigDecimal>>(emptyMap())
    private val busy = MutableStateFlow(false)

    val month: StateFlow<YearMonth> = monthState.asStateFlow()

    val uiState: StateFlow<BudgetUiState> = combine(
        monthState.flatMapLatest { month -> budget.month(month).map { month to it } },
        catalog.categories,
        pending,
        busy,
    ) { (month, state), categories, pendingPlans, isBusy ->
        val data = state.dataOrNull
        when {
            data != null -> content(month, state, data, categories.dataOrNull.orEmpty(), pendingPlans, isBusy)
            state.errorOrNull != null && !state.isRefreshing -> BudgetUiState.Error(month, state.errorOrNull!!)
            else -> BudgetUiState.Loading(month)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BudgetUiState.Loading(monthState.value))

    fun setMonth(month: YearMonth) {
        savedState[KEY_MONTH] = month.toString()
        monthState.value = month
    }

    fun refresh() = budget.invalidate(monthState.value)

    /** Saves the plan of a category; the screen shows the new value at once and goes back on failure. */
    fun assign(categoryId: Long, assigned: BigDecimal, onError: (ApiError) -> Unit) {
        val month = monthState.value
        pending.update { it + (categoryId to assigned) }
        viewModelScope.launch {
            val result = budget.assign(month, categoryId, assigned)
            pending.update { it - categoryId }
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    fun move(from: Long, to: Long, amount: BigDecimal, onResult: (ApiError?) -> Unit) = run {
        busy.value = true
        viewModelScope.launch {
            val result = budget.move(monthState.value, from, to, amount)
            busy.value = false
            onResult((result as? ApiResult.Err)?.error)
        }
    }

    fun copyPrevious(onError: (ApiError) -> Unit) {
        busy.value = true
        viewModelScope.launch {
            val result = budget.copyPrevious(monthState.value)
            busy.value = false
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    /** The plan the category had last month, for the shortcut in the plan sheet. */
    fun previousAssigned(categoryId: Long, onResult: (BigDecimal?) -> Unit) {
        viewModelScope.launch {
            onResult((budget.previousAssigned(monthState.value, categoryId) as? ApiResult.Ok)?.value)
        }
    }

    private fun content(
        month: YearMonth,
        state: CacheState<BudgetMonth>,
        data: BudgetMonth,
        categories: List<Category>,
        pendingPlans: Map<Long, BigDecimal>,
        isBusy: Boolean,
    ): BudgetUiState.Content {
        val flat = categories.flatMap { it.flatten() }
        val carryOver = flat.filter { it.carryOver }.map { it.id }.toSet()
        return BudgetUiState.Content(
            month = month,
            data = data,
            groups = BudgetModel.groupEnvelopes(data.rows, pendingPlans, carryOver),
            overspent = BudgetModel.overspent(data.rows),
            progress = BudgetModel.monthProgress(month, clock.today()),
            categoriesById = flat.associateBy { it.id },
            pending = pendingPlans.keys,
            stale = state is CacheState.Stale,
            fetchedAt = (state as? CacheState.Stale)?.fetchedAt,
            offline = state.errorOrNull is ApiError.Network,
            refreshing = state.isRefreshing,
            busy = isBusy,
        )
    }

    companion object {
        private const val KEY_MONTH = "month"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
