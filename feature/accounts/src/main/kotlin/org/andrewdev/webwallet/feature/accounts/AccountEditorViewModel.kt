package org.andrewdev.webwallet.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.data.AccountDraft
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.UserRepository
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.ErrorCode
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

data class AccountEditorUiState(
    val loaded: Boolean = false,
    val editing: Account? = null,
    /** The key of this creation: a retry after a lost response does not create the account twice. */
    val creationKey: UUID = UUID.randomUUID(),
    val name: String = "",
    val type: AccountType = AccountType.CARD,
    val currency: String = "UAH",
    val openingBalanceText: String = "",
    val openingBalance: BigDecimal? = null,
    val creditLimitText: String = "",
    val creditLimit: BigDecimal? = null,
    val interestRateText: String = "",
    val inBudget: Boolean = true,
    val archived: Boolean = false,
    val saving: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val validated: Boolean = false,
    /** RESOURCE_IN_USE on delete: the account has transactions. */
    val deleteBlocked: Boolean = false,
    val dirty: Boolean = false,
) {
    val isNew: Boolean get() = editing == null
    val interestRate: BigDecimal get() = interestRateText.replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
    val nameMissing: Boolean get() = validated && name.isBlank()
}

/** The account form (docs/04 §5.2): the currency is locked after creation, the type is not. */
@HiltViewModel
class AccountEditorViewModel @Inject constructor(
    private val accounts: AccountsRepository,
    private val user: UserRepository,
) : ViewModel() {

    private val state = MutableStateFlow(AccountEditorUiState())
    val uiState: StateFlow<AccountEditorUiState> = state.asStateFlow()

    fun load(id: Long?) {
        if (state.value.loaded) return
        viewModelScope.launch {
            val base = user.user.first().dataOrNull?.baseCurrency?.code ?: "UAH"
            val account = id?.let { wanted -> accounts.accounts.first().dataOrNull?.firstOrNull { it.id == wanted } }
            state.update {
                if (account == null) {
                    it.copy(loaded = true, currency = base)
                } else {
                    it.copy(
                        loaded = true,
                        editing = account,
                        name = account.name,
                        type = account.type,
                        currency = account.currency.code,
                        openingBalanceText = account.openingBalance.toPlainString(),
                        openingBalance = account.openingBalance,
                        creditLimitText = account.creditLimit?.toPlainString().orEmpty(),
                        creditLimit = account.creditLimit,
                        interestRateText = account.interestRate?.toPlainString().orEmpty(),
                        inBudget = account.inBudget,
                        archived = account.archived,
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = state.update {
        it.copy(name = value, dirty = true, fieldErrors = it.fieldErrors - "name")
    }

    fun onTypeChange(type: AccountType) = state.update {
        it.copy(type = type, dirty = true, inBudget = if (it.isNew) type.inBudgetByDefault else it.inBudget)
    }

    fun onCurrencyChange(code: String) = state.update { it.copy(currency = code, dirty = true) }

    fun onOpeningBalanceChange(text: String, value: BigDecimal?) =
        state.update { it.copy(openingBalanceText = text, openingBalance = value, dirty = true) }

    fun onCreditLimitChange(text: String, value: BigDecimal?) =
        state.update { it.copy(creditLimitText = text, creditLimit = value, dirty = true) }

    fun onInterestRateChange(text: String) = state.update { it.copy(interestRateText = text, dirty = true) }

    fun onInBudgetChange(value: Boolean) = state.update { it.copy(inBudget = value, dirty = true) }

    fun onArchivedChange(value: Boolean) = state.update { it.copy(archived = value, dirty = true) }

    fun save(onSaved: () -> Unit) {
        val current = state.value.copy(validated = true)
        state.value = current
        if (current.nameMissing || current.saving) return
        state.update { it.copy(saving = true, error = null) }
        val draft = AccountDraft(
            name = current.name.trim(),
            type = current.type,
            currency = current.currency.takeIf { current.isNew },
            openingBalance = current.openingBalance ?: if (current.isNew) BigDecimal.ZERO else null,
            creditLimit = if (current.type.hasCreditLimit) current.creditLimit ?: BigDecimal.ZERO else null,
            interestRate = if (current.type.hasInterest) current.interestRate else null,
            inBudget = current.inBudget,
            archived = if (current.isNew) null else current.archived,
        )
        viewModelScope.launch {
            val result = current.editing?.let { accounts.update(it.id, draft) }
                ?: accounts.create(draft, current.creationKey)
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

    fun delete(onDeleted: () -> Unit) {
        val id = state.value.editing?.id ?: return
        state.update { it.copy(saving = true, error = null, deleteBlocked = false) }
        viewModelScope.launch {
            when (val result = accounts.delete(id)) {
                is ApiResult.Ok -> onDeleted()
                is ApiResult.Err -> {
                    val blocked = (result.error as? ApiError.Problem)?.code == ErrorCode.RESOURCE_IN_USE
                    state.update {
                        it.copy(
                            saving = false,
                            error = result.error.takeIf { !blocked },
                            deleteBlocked = blocked,
                        )
                    }
                }
            }
        }
    }
}
