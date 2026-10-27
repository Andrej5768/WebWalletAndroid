package org.andrewdev.webwallet.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.CatalogRepository
import org.andrewdev.webwallet.core.data.RecurringRepository
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.Occurrence
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** The planned group of the feed (docs/04 §6.1): the pending payments around today. */
data class PlannedUiState(
    val days: List<Pair<LocalDate, List<PlannedRow>>>,
    /** All pending dates in range, also the ones folded away. */
    val total: Int,
    val until: LocalDate?,
    /** How many rows the collapsed group hides. */
    val hidden: Int,
    val expanded: Boolean,
    val accountsById: Map<Long, Account>,
    val categoriesById: Map<Long, Category>,
    val today: LocalDate,
    /** Occurrences whose confirm, link or skip is running, by series and date. */
    val busy: Set<Pair<Long, LocalDate>>,
) {
    val isEmpty: Boolean get() = total == 0

    companion object {
        val EMPTY = PlannedUiState(emptyList(), 0, null, 0, false, emptyMap(), emptyMap(), LocalDate.EPOCH, emptySet())
    }
}

/**
 * Pending occurrences for the feed: collapsed, the overdue ones and the nearest upcoming; expanded, everything in
 * range with a frequent series folded into its nearest date (web `PlannedGroup`).
 */
@HiltViewModel
class PlannedViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    accounts: AccountsRepository,
    catalog: CatalogRepository,
    private val clock: KyivClock,
) : ViewModel() {

    private val expanded = MutableStateFlow(false)
    private val busy = MutableStateFlow<Set<Pair<Long, LocalDate>>>(emptySet())

    val uiState: StateFlow<PlannedUiState> = combine(
        recurring.occurrences,
        accounts.accounts,
        catalog.categories,
        expanded,
        busy,
    ) { state, accountState, categoryState, isExpanded, running ->
        val pending = state.dataOrNull.orEmpty().filter { it.transactionId == null }.sortedBy { it.date }
        val rows = Planned.rows(pending)
        val shown = if (isExpanded) rows else collapsed(rows)
        PlannedUiState(
            days = Planned.byDayDescending(shown),
            total = pending.size,
            until = pending.lastOrNull()?.date,
            hidden = rows.size - shown.size,
            expanded = isExpanded,
            accountsById = accountState.dataOrNull.orEmpty().associateBy { it.id },
            categoriesById = categoryState.dataOrNull.orEmpty().flatMap { it.flatten() }.associateBy { it.id },
            today = clock.today(),
            busy = running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlannedUiState.EMPTY)

    fun toggleExpanded() = expanded.update { !it }

    fun refresh() = recurring.invalidate()

    /** Saves the occurrence as a transaction; a variable amount comes from the sheet. */
    fun confirm(occurrence: Occurrence, date: LocalDate?, amount: BigDecimal?, onError: (ApiError) -> Unit) =
        run(occurrence, onError) { recurring.confirm(it, date, amount) }

    fun link(occurrence: Occurrence, transactionId: Long, onError: (ApiError) -> Unit) =
        run(occurrence, onError) { recurring.link(it, transactionId) }

    fun skip(occurrence: Occurrence, onError: (ApiError) -> Unit) = run(occurrence, onError) { recurring.skip(it) }

    fun candidates(occurrence: Occurrence, onResult: (ApiResult<List<Transaction>>) -> Unit) {
        viewModelScope.launch { onResult(recurring.candidates(occurrence)) }
    }

    private fun run(
        occurrence: Occurrence,
        onError: (ApiError) -> Unit,
        action: suspend (Occurrence) -> ApiResult<Occurrence>,
    ) {
        val key = occurrence.recurringId to occurrence.date
        if (key in busy.value) return
        busy.update { it + key }
        viewModelScope.launch {
            val result = action(occurrence)
            busy.update { it - key }
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Upcoming payments shown when the group is collapsed, besides the overdue ones. */
        private const val NEAREST = 3

        fun collapsed(rows: List<PlannedRow>): List<PlannedRow> {
            val overdue = rows.filter { it.occurrence.overdue }
            val upcoming = rows.filter { !it.occurrence.overdue }.take(NEAREST)
            return overdue + upcoming
        }
    }
}

/** What the planned group can do with an occurrence. */
class PlannedActions(
    val confirm: (Occurrence, date: LocalDate?, amount: BigDecimal?) -> Unit,
    val link: (Occurrence, transactionId: Long) -> Unit,
    val skip: (Occurrence) -> Unit,
    val candidates: (Occurrence, onResult: (ApiResult<List<Transaction>>) -> Unit) -> Unit,
    val editSeries: (recurringId: Long) -> Unit,
    val toggleExpanded: () -> Unit,
)
