package org.andrewdev.webwallet.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.CacheState
import org.andrewdev.webwallet.core.data.CatalogRepository
import org.andrewdev.webwallet.core.data.RecurringRepository
import org.andrewdev.webwallet.core.data.UserRepository
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.CategoryKind
import org.andrewdev.webwallet.core.model.Frequency
import org.andrewdev.webwallet.core.model.Payee
import org.andrewdev.webwallet.core.model.Recurring
import org.andrewdev.webwallet.core.model.RecurringDraft
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

// --- list

sealed interface RecurringListUiState {
    data object Loading : RecurringListUiState

    data class Error(val error: ApiError) : RecurringListUiState

    data class Content(
        val active: List<Recurring>,
        val stopped: List<Recurring>,
        val accountsById: Map<Long, Account>,
        val baseCurrency: String,
        /** Expenses and income per month in the base currency, weekly and yearly payments converted. */
        val monthlyOut: BigDecimal,
        val monthlyIn: BigDecimal,
        val stale: Boolean,
        val fetchedAt: Instant?,
        val offline: Boolean,
        val refreshing: Boolean,
    ) : RecurringListUiState {
        val isEmpty: Boolean get() = active.isEmpty() && stopped.isEmpty()
    }
}

/** The recurring payments (docs/04 §6.4 list): by name, the stopped ones at the end, monthly totals on top. */
@HiltViewModel
class RecurringListViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    accounts: AccountsRepository,
    user: UserRepository,
) : ViewModel() {

    val uiState: StateFlow<RecurringListUiState> = combine(
        recurring.list,
        accounts.accounts,
        user.user,
    ) { list, accountState, userState ->
        when (list) {
            is CacheState.Missing -> {
                val error = list.error
                if (error != null && !list.loading) RecurringListUiState.Error(error) else RecurringListUiState.Loading
            }
            else -> {
                val items = list.dataOrNull.orEmpty().sortedBy { it.name.lowercase() }
                val accountList = accountState.dataOrNull.orEmpty()
                val base = userState.dataOrNull?.baseCurrency?.code ?: "UAH"
                val rates = ratesFromAccounts(accountList, base)
                val active = items.filter { !it.stopped }
                RecurringListUiState.Content(
                    active = active,
                    stopped = items.filter { it.stopped },
                    accountsById = accountList.associateBy { it.id },
                    baseCurrency = base,
                    monthlyOut = monthly(active, TransactionType.EXPENSE, rates),
                    monthlyIn = monthly(active, TransactionType.INCOME, rates),
                    stale = list is CacheState.Stale,
                    fetchedAt = (list as? CacheState.Stale)?.fetchedAt,
                    offline = list.errorOrNull is ApiError.Network,
                    refreshing = list.isRefreshing,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecurringListUiState.Loading)

    fun refresh() = recurring.invalidate()

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MONEY_SCALE = 2

        /** The rate of each currency to the base, from the accounts that carry both balances (web). */
        fun ratesFromAccounts(accounts: List<Account>, base: String): Map<String, BigDecimal> {
            val rates = mutableMapOf(base to BigDecimal.ONE)
            accounts.forEach { account ->
                val inBase = account.balanceInBase ?: return@forEach
                if (account.currency.code !in rates && account.balance.signum() != 0) {
                    rates[account.currency.code] = inBase.divide(account.balance, RATE_SCALE, RoundingMode.HALF_UP)
                }
            }
            return rates
        }

        /** Payments of the type per month in the base currency; those without a rate are left out. */
        fun monthly(items: List<Recurring>, type: TransactionType, rates: Map<String, BigDecimal>): BigDecimal =
            items.filter { it.type == type }.fold(BigDecimal.ZERO) { sum, item ->
                val rate = rates[item.currency.code] ?: return@fold sum
                val perMonth = BigDecimal.valueOf(Schedule.perMonth(item.frequency, item.interval))
                sum + (item.amount * rate * perMonth)
            }.setScale(MONEY_SCALE, RoundingMode.HALF_UP)

        private const val RATE_SCALE = 8
    }
}

// --- editor

enum class EndMode { NEVER, DATE, COUNT }

data class RecurringEditorUiState(
    val loaded: Boolean = false,
    val editing: Recurring? = null,
    val creationKey: UUID = UUID.randomUUID(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val payees: List<Payee> = emptyList(),
    val baseCurrency: String = "UAH",
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val amountText: String = "",
    val amount: BigDecimal? = null,
    val toAmountText: String = "",
    val toAmount: BigDecimal? = null,
    val variableAmount: Boolean = false,
    val categoryId: Long? = null,
    val payee: String = "",
    val comment: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val intervalText: String = "1",
    val weekday: DayOfWeek = DayOfWeek.MONDAY,
    val dayOfMonth: Int = 1,
    val yearMonth: Int = 1,
    /** The day the schedule counts from; weekly and yearly schedules are aligned to their weekday or day. */
    val startFrom: LocalDate = LocalDate.EPOCH,
    val endMode: EndMode = EndMode.NEVER,
    val endDate: LocalDate? = null,
    val countText: String = "12",
    val autoCreate: Boolean = false,
    val tolerance: Int = DEFAULT_TOLERANCE,
    val saving: Boolean = false,
    val validated: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isNew: Boolean get() = editing == null
    val isTransfer: Boolean get() = type == TransactionType.TRANSFER
    val interval: Int get() = intervalText.toIntOrNull()?.coerceIn(1, MAX_INTERVAL) ?: 1
    val count: Int get() = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val toAccount: Account? get() = accounts.firstOrNull { it.id == toAccountId }
    val currency: String get() = account?.currency?.code ?: baseCurrency

    /** The transfer goes between currencies, so the received amount is asked too. */
    val needsToAmount: Boolean get() = isTransfer && toAccount != null && toAccount?.currency != account?.currency

    /** The first occurrence: the start aligned to the weekday or the day of the year. */
    val startDate: LocalDate
        get() = when (frequency) {
            Frequency.MONTHLY -> monthlyStart()
            else -> Schedule.alignedStart(startFrom, frequency, weekday, yearMonth, dayOfMonth)
        }

    /** The day of the month on or after the start; a day the month lacks falls on its last day. */
    private fun monthlyStart(): LocalDate {
        val sameMonth = startFrom.withDayOfMonth(minOf(dayOfMonth, startFrom.lengthOfMonth()))
        if (!sameMonth.isBefore(startFrom)) return sameMonth
        val next = startFrom.plusMonths(1)
        return next.withDayOfMonth(minOf(dayOfMonth, next.lengthOfMonth()))
    }

    val resolvedEndDate: LocalDate?
        get() = when (endMode) {
            EndMode.NEVER -> null
            EndMode.DATE -> endDate
            EndMode.COUNT -> Schedule.endDateForCount(startDate, frequency, interval, count)
        }

    val nameMissing: Boolean get() = validated && name.isBlank()
    val amountMissing: Boolean get() = validated && (amount == null || amount.signum() <= 0)
    val accountMissing: Boolean get() = validated && accountId == null
    val toAccountMissing: Boolean get() = validated && isTransfer && (toAccountId == null || toAccountId == accountId)
    val endBeforeStart: Boolean get() = endMode == EndMode.DATE && endDate != null && endDate.isBefore(startDate)
    val hasErrors: Boolean
        get() = nameMissing || amountMissing || accountMissing || toAccountMissing || endBeforeStart

    val selectableCategories: List<Category>
        get() {
            val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            return categories.filter { it.kind == kind && !it.archived }
        }

    companion object {
        const val DEFAULT_TOLERANCE = 10
        const val VARIABLE_TOLERANCE = 25
        const val MAX_INTERVAL = 365
    }
}

/** The recurring payment editor (docs/04 §6.4): the template of the transaction and its schedule. */
@Suppress("TooManyFunctions")
@HiltViewModel
class RecurringEditorViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    private val accounts: AccountsRepository,
    private val catalog: CatalogRepository,
    private val user: UserRepository,
    private val clock: KyivClock,
) : ViewModel() {

    private val state = MutableStateFlow(RecurringEditorUiState())
    val uiState: StateFlow<RecurringEditorUiState> = state.asStateFlow()

    fun load(id: Long?) {
        if (state.value.loaded) return
        viewModelScope.launch {
            val accountList = accounts.accounts.first().dataOrNull.orEmpty().filter { !it.archived }
            val categories = catalog.categories.first().dataOrNull.orEmpty()
            val payees = catalog.payees.first().dataOrNull.orEmpty()
            val base = user.user.first().dataOrNull?.baseCurrency?.code ?: "UAH"
            val today = clock.today()
            val blank = state.value.copy(
                accounts = accountList,
                categories = categories,
                payees = payees,
                baseCurrency = base,
                startFrom = today,
                dayOfMonth = today.dayOfMonth,
                yearMonth = today.monthValue,
                weekday = today.dayOfWeek,
            )
            if (id == null) {
                state.value = blank.copy(loaded = true)
                return@launch
            }
            when (val result = recurring.get(id)) {
                is ApiResult.Ok -> state.value = blank.edit(result.value)
                is ApiResult.Err -> state.update { it.copy(error = result.error) }
            }
        }
    }

    private fun RecurringEditorUiState.edit(item: Recurring): RecurringEditorUiState = copy(
        loaded = true,
        editing = item,
        name = item.name,
        type = item.type,
        accountId = item.accountId,
        toAccountId = item.toAccountId,
        amountText = item.amount.toPlainString(),
        amount = item.amount,
        toAmountText = item.toAmount?.toPlainString().orEmpty(),
        toAmount = item.toAmount,
        variableAmount = item.variableAmount,
        categoryId = item.categoryId,
        payee = item.payee?.name.orEmpty(),
        comment = item.comment.orEmpty(),
        frequency = item.frequency,
        intervalText = item.interval.toString(),
        weekday = item.startDate.dayOfWeek,
        dayOfMonth = item.startDate.dayOfMonth,
        yearMonth = item.startDate.monthValue,
        startFrom = item.startDate,
        endMode = if (item.endDate == null) EndMode.NEVER else EndMode.DATE,
        endDate = item.endDate,
        autoCreate = item.autoCreate,
        tolerance = item.tolerance,
    )

    fun onNameChange(value: String) = state.update { it.copy(name = value, fieldErrors = it.fieldErrors - "name") }

    fun onTypeChange(type: TransactionType) = state.update {
        val toAccountId = if (type == TransactionType.TRANSFER) it.toAccountId else null
        it.copy(type = type, categoryId = null, toAccountId = toAccountId)
    }

    fun onAccountChange(id: Long) = state.update { it.copy(accountId = id) }

    fun onToAccountChange(id: Long) = state.update { it.copy(toAccountId = id) }

    fun onAmountChange(text: String, value: BigDecimal?) = state.update {
        it.copy(amountText = text, amount = value, fieldErrors = it.fieldErrors - "amount")
    }

    fun onToAmountChange(text: String, value: BigDecimal?) = state.update {
        it.copy(toAmountText = text, toAmount = value)
    }

    /** A variable amount matches more loosely by default; a tolerance the user set stays. */
    fun onVariableChange(variable: Boolean) = state.update {
        val default = RecurringEditorUiState.DEFAULT_TOLERANCE
        val loose = RecurringEditorUiState.VARIABLE_TOLERANCE
        val tolerance = when {
            variable && it.tolerance == default -> loose
            !variable && it.tolerance == loose -> default
            else -> it.tolerance
        }
        it.copy(variableAmount = variable, tolerance = tolerance)
    }

    fun onCategoryChange(id: Long?) = state.update { it.copy(categoryId = id) }

    fun onPayeeChange(value: String) = state.update { it.copy(payee = value) }

    fun onCommentChange(value: String) = state.update { it.copy(comment = value) }

    fun onFrequencyChange(frequency: Frequency) = state.update { it.copy(frequency = frequency) }

    fun onIntervalChange(text: String) = state.update {
        it.copy(intervalText = text.filter(Char::isDigit).take(INTERVAL_DIGITS))
    }

    fun onWeekdayChange(weekday: DayOfWeek) = state.update { it.copy(weekday = weekday) }

    fun onDayOfMonthChange(day: Int) = state.update { it.copy(dayOfMonth = day.coerceIn(1, DAYS_IN_MONTH)) }

    fun onYearDayChange(month: Int, day: Int) = state.update { it.copy(yearMonth = month, dayOfMonth = day) }

    fun onStartFromChange(date: LocalDate) = state.update { it.copy(startFrom = date) }

    fun onEndModeChange(mode: EndMode) = state.update { it.copy(endMode = mode) }

    fun onEndDateChange(date: LocalDate) = state.update { it.copy(endDate = date) }

    fun onCountChange(text: String) = state.update {
        it.copy(countText = text.filter(Char::isDigit).take(COUNT_DIGITS))
    }

    fun onAutoCreateChange(auto: Boolean) = state.update { it.copy(autoCreate = auto) }

    fun onToleranceChange(value: Int) = state.update { it.copy(tolerance = value.coerceIn(0, MAX_TOLERANCE)) }

    fun save(onSaved: () -> Unit) {
        val current = state.value.copy(validated = true)
        state.value = current
        if (current.hasErrors || current.saving) return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val draft = current.toDraft()
            val result = current.editing?.let { recurring.update(it.id, draft) }
                ?: recurring.create(draft, current.creationKey)
            when (result) {
                is ApiResult.Ok -> onSaved()
                is ApiResult.Err -> fail(result.error)
            }
        }
    }

    /** "Stop": the series ends today, what was paid stays. */
    fun stop(onDone: () -> Unit) {
        val current = state.value
        val editing = current.editing ?: return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val draft = current.copy(endMode = EndMode.DATE, endDate = clock.today()).toDraft()
            when (val result = recurring.update(editing.id, draft)) {
                is ApiResult.Ok -> onDone()
                is ApiResult.Err -> fail(result.error)
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = state.value.editing?.id ?: return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = recurring.delete(id)) {
                is ApiResult.Ok -> onDeleted()
                is ApiResult.Err -> fail(result.error)
            }
        }
    }

    private fun RecurringEditorUiState.toDraft(): RecurringDraft {
        val payeeName = payee.trim().ifEmpty { null }
        val known = payees.firstOrNull { it.name.equals(payeeName, ignoreCase = true) }
        return RecurringDraft(
            name = name,
            type = type,
            accountId = accountId!!,
            amount = amount!!,
            toAccountId = if (isTransfer) toAccountId else null,
            toAmount = if (needsToAmount) toAmount else null,
            categoryId = if (isTransfer) null else categoryId,
            payeeId = if (isTransfer) null else known?.id,
            payeeName = if (isTransfer || known != null) null else payeeName,
            comment = comment,
            frequency = frequency,
            interval = interval,
            startDate = startDate,
            endDate = resolvedEndDate,
            autoCreate = autoCreate,
            variableAmount = variableAmount,
            tolerance = tolerance,
        )
    }

    private fun fail(error: ApiError) = state.update {
        it.copy(
            saving = false,
            error = error,
            fieldErrors = (error as? ApiError.Problem)?.fieldErrors.orEmpty(),
        )
    }

    companion object {
        private const val DAYS_IN_MONTH = 31
        private const val MAX_TOLERANCE = 50
        private const val INTERVAL_DIGITS = 3
        private const val COUNT_DIGITS = 4
    }
}

/** What the editor asks the view model to do. */
@Suppress("LongParameterList")
class RecurringEditorCallbacks(
    val onNameChange: (String) -> Unit,
    val onTypeChange: (TransactionType) -> Unit,
    val onAccountChange: (Long) -> Unit,
    val onToAccountChange: (Long) -> Unit,
    val onAmountChange: (String, BigDecimal?) -> Unit,
    val onToAmountChange: (String, BigDecimal?) -> Unit,
    val onVariableChange: (Boolean) -> Unit,
    val onCategoryChange: (Long?) -> Unit,
    val onPayeeChange: (String) -> Unit,
    val onCommentChange: (String) -> Unit,
    val onFrequencyChange: (Frequency) -> Unit,
    val onIntervalChange: (String) -> Unit,
    val onWeekdayChange: (DayOfWeek) -> Unit,
    val onDayOfMonthChange: (Int) -> Unit,
    val onYearDayChange: (month: Int, day: Int) -> Unit,
    val onStartFromChange: (LocalDate) -> Unit,
    val onEndModeChange: (EndMode) -> Unit,
    val onEndDateChange: (LocalDate) -> Unit,
    val onCountChange: (String) -> Unit,
    val onAutoCreateChange: (Boolean) -> Unit,
    val onToleranceChange: (Int) -> Unit,
    val onSave: () -> Unit,
    val onStop: () -> Unit,
    val onDelete: () -> Unit,
    val onClose: () -> Unit,
)
