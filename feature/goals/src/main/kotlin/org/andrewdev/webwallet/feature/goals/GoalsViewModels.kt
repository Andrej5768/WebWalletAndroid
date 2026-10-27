package org.andrewdev.webwallet.feature.goals

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
import org.andrewdev.webwallet.core.data.GoalsRepository
import org.andrewdev.webwallet.core.data.UserRepository
import org.andrewdev.webwallet.core.datastore.CalculatorInputs
import org.andrewdev.webwallet.core.datastore.SettingsStore
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountSummary
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.model.Goal
import org.andrewdev.webwallet.core.model.GoalDraft
import org.andrewdev.webwallet.core.model.GoalsSummary
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

// --- goals list, with the averages the calculators share

sealed interface GoalsUiState {
    data object Loading : GoalsUiState

    data class Error(val error: ApiError) : GoalsUiState

    data class Content(
        val summary: GoalsSummary,
        val accountsById: Map<Long, Account>,
        val accounts: List<Account>,
        val accountSummary: AccountSummary?,
        val averages: Calculators.Averages,
        val baseCurrency: String,
        val calculators: CalculatorInputs,
        /** Goals whose change is running. */
        val busy: Set<Long>,
        val stale: Boolean,
        val fetchedAt: Instant?,
        val offline: Boolean,
        val refreshing: Boolean,
    ) : GoalsUiState {
        val isEmpty: Boolean get() = summary.goals.isEmpty()
    }
}

/** The goals tab and the three calculators (docs/04 §10): the goals from the server, the averages of the year. */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goals: GoalsRepository,
    accounts: AccountsRepository,
    user: UserRepository,
    private val settings: SettingsStore,
    private val clock: KyivClock,
) : ViewModel() {

    private val busy = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<GoalsUiState> = combine(
        goals.goals,
        goals.twelveMonths,
        combine(accounts.accounts, accounts.summary) { list, summary -> list to summary },
        combine(user.user, settings.data) { u, s -> u to s },
        busy,
    ) { state, months, (accountState, summaryState), (userState, prefs), running ->
        val summary = state.dataOrNull
        when {
            summary != null -> {
                val accountList = accountState.dataOrNull.orEmpty()
                val figures = months.dataOrNull?.second.orEmpty().map {
                    Calculators.MonthFigures(it.month, it.income.toDouble(), it.expense.toDouble())
                }
                GoalsUiState.Content(
                    summary = summary,
                    accountsById = accountList.associateBy { it.id },
                    accounts = accountList.filter { !it.archived },
                    accountSummary = summaryState.dataOrNull,
                    averages = Calculators.averagesOf(figures, clock.thisMonth().toString()),
                    baseCurrency = userState.dataOrNull?.baseCurrency?.code ?: summary.currency.code,
                    calculators = prefs.calculators,
                    busy = running,
                    stale = state is CacheState.Stale,
                    fetchedAt = (state as? CacheState.Stale)?.fetchedAt,
                    offline = state.errorOrNull is ApiError.Network,
                    refreshing = state.isRefreshing,
                )
            }
            state.errorOrNull != null && !state.isRefreshing -> GoalsUiState.Error(state.errorOrNull!!)
            else -> GoalsUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GoalsUiState.Loading)

    fun refresh() = goals.invalidate()

    fun setAchieved(goal: Goal, achieved: Boolean, onError: (ApiError) -> Unit) =
        run(goal.id, onError) { goals.setAchieved(goal, achieved) }

    fun delete(goal: Goal, onError: (ApiError) -> Unit) = run(goal.id, onError) { goals.delete(goal.id) }

    /** Adds to or takes from a goal tracked by hand; the key makes a retry safe. */
    fun contribute(goal: Goal, amount: BigDecimal, key: UUID, onError: (ApiError) -> Unit) =
        run(goal.id, onError) { goals.contribute(goal.id, amount, key) }

    /** The inputs of FIRE, FYM and the calculator survive restarts, like the web's local storage. */
    fun saveCalculators(transform: (CalculatorInputs) -> CalculatorInputs) {
        viewModelScope.launch { settings.update { it.copy(calculators = transform(it.calculators)) } }
    }

    private fun run(id: Long, onError: (ApiError) -> Unit, action: suspend () -> ApiResult<*>) {
        if (id in busy.value) return
        busy.update { it + id }
        viewModelScope.launch {
            val result = action()
            busy.update { it - id }
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Money that can be spent at once; deposits, investments and debts are not (web FYM). */
        val LIQUID_BY_DEFAULT = setOf(AccountType.CASH, AccountType.CARD, AccountType.FOP, AccountType.SAVINGS)
    }
}

// --- editor

data class GoalEditorUiState(
    val loaded: Boolean = false,
    val editing: Goal? = null,
    val creationKey: UUID = UUID.randomUUID(),
    val accounts: List<Account> = emptyList(),
    val baseCurrency: String = "UAH",
    val name: String = "",
    val targetText: String = "",
    val target: BigDecimal? = null,
    val currency: String = "UAH",
    val targetDate: LocalDate? = null,
    val accountId: Long? = null,
    val savedText: String = "",
    val saved: BigDecimal? = null,
    val icon: String? = null,
    val color: String? = null,
    val saving: Boolean = false,
    val validated: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isNew: Boolean get() = editing == null
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val nameMissing: Boolean get() = validated && name.isBlank()
    val targetMissing: Boolean get() = validated && (target == null || target.signum() <= 0)
    val hasErrors: Boolean get() = nameMissing || targetMissing

    /** With an account the progress follows its balance, so the currency is the account's. */
    val effectiveCurrency: String get() = account?.currency?.code ?: currency
}

/** The goal editor (docs/04 §10.1): name, target, currency, date, linked account or manual progress, icon, colour. */
@HiltViewModel
class GoalEditorViewModel @Inject constructor(
    private val goals: GoalsRepository,
    private val accounts: AccountsRepository,
    private val user: UserRepository,
) : ViewModel() {

    private val state = MutableStateFlow(GoalEditorUiState())
    val uiState: StateFlow<GoalEditorUiState> = state.asStateFlow()

    fun load(id: Long?) {
        if (state.value.loaded) return
        viewModelScope.launch {
            val accountList = accounts.accounts.first().dataOrNull.orEmpty().filter { !it.archived }
            val base = user.user.first().dataOrNull?.baseCurrency?.code ?: "UAH"
            val blank = state.value.copy(accounts = accountList, baseCurrency = base, currency = base)
            val goal = id?.let { goalId -> goals.goals.first().dataOrNull?.goals?.firstOrNull { it.id == goalId } }
            state.value = if (goal == null) blank.copy(loaded = true) else blank.edit(goal)
        }
    }

    private fun GoalEditorUiState.edit(goal: Goal) = copy(
        loaded = true,
        editing = goal,
        name = goal.name,
        targetText = goal.targetAmount.toPlainString(),
        target = goal.targetAmount,
        currency = goal.currency.code,
        targetDate = goal.targetDate,
        accountId = goal.accountId,
        savedText = if (goal.accountId == null) goal.saved.toPlainString() else "",
        saved = if (goal.accountId == null) goal.saved else null,
        icon = goal.icon,
        color = goal.color,
    )

    fun onNameChange(value: String) = state.update { it.copy(name = value, fieldErrors = it.fieldErrors - "name") }

    fun onTargetChange(text: String, value: BigDecimal?) = state.update {
        it.copy(targetText = text, target = value, fieldErrors = it.fieldErrors - "targetAmount")
    }

    fun onCurrencyChange(code: String) = state.update { it.copy(currency = code) }

    fun onDateChange(date: LocalDate?) = state.update { it.copy(targetDate = date) }

    fun onAccountChange(id: Long?) = state.update { it.copy(accountId = id) }

    fun onSavedChange(text: String, value: BigDecimal?) = state.update { it.copy(savedText = text, saved = value) }

    fun onIconChange(icon: String?) = state.update { it.copy(icon = icon) }

    fun onColorChange(color: String?) = state.update { it.copy(color = color) }

    fun save(onSaved: () -> Unit) {
        val current = state.value.copy(validated = true)
        state.value = current
        if (current.hasErrors || current.saving) return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val draft = GoalDraft(
                name = current.name,
                targetAmount = current.target!!,
                currency = current.effectiveCurrency,
                targetDate = current.targetDate,
                accountId = current.accountId,
                saved = if (current.accountId == null) current.saved ?: BigDecimal.ZERO else null,
                icon = current.icon,
                color = current.color,
                achieved = current.editing?.achieved,
                sortOrder = current.editing?.sortOrder,
            )
            val result = current.editing?.let { goals.update(it.id, draft) } ?: goals.create(draft, current.creationKey)
            when (result) {
                is ApiResult.Ok -> onSaved()
                is ApiResult.Err -> state.update {
                    it.copy(
                        saving = false,
                        error = result.error,
                        fieldErrors = (result.error as? ApiError.Problem)?.fieldErrors.orEmpty(),
                    )
                }
            }
        }
    }
}

/** Where the goals screen sends the user. */
class GoalsActions(val openEditor: (id: Long?) -> Unit, val back: () -> Unit)

/** What the editor asks the view model to do. */
@Suppress("LongParameterList")
class GoalEditorCallbacks(
    val onNameChange: (String) -> Unit,
    val onTargetChange: (String, BigDecimal?) -> Unit,
    val onCurrencyChange: (String) -> Unit,
    val onDateChange: (LocalDate?) -> Unit,
    val onAccountChange: (Long?) -> Unit,
    val onSavedChange: (String, BigDecimal?) -> Unit,
    val onIconChange: (String?) -> Unit,
    val onColorChange: (String?) -> Unit,
    val onSave: () -> Unit,
    val onClose: () -> Unit,
)
