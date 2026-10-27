package org.andrewdev.webwallet.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.data.AccountDraft
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.CatalogRepository
import org.andrewdev.webwallet.core.data.TransactionDraft
import org.andrewdev.webwallet.core.data.TransactionsRepository
import org.andrewdev.webwallet.core.data.UserRepository
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.CategoryKind
import org.andrewdev.webwallet.core.model.Payee
import org.andrewdev.webwallet.core.model.Split
import org.andrewdev.webwallet.core.model.Tag
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.ErrorCode
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** What the form offers: the API types plus a debt, saved as a transfer with the debt account (web `FormType`). */
enum class FormType { EXPENSE, INCOME, TRANSFER, DEBT }

data class SplitValue(
    val categoryId: Long? = null,
    val amountText: String = "",
    val amount: BigDecimal? = null,
    val comment: String = "",
)

data class EditorUiState(
    val loaded: Boolean = false,
    val editing: Transaction? = null,
    /** The key of this creation: a retry after a lost response does not create the transaction twice. */
    val creationKey: UUID = UUID.randomUUID(),
    val type: FormType = FormType.EXPENSE,
    val debtDirection: DebtDirection = DebtDirection.GAVE,
    val amountText: String = "",
    val amount: BigDecimal? = null,
    val date: LocalDate = LocalDate.EPOCH,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val toAmountText: String = "",
    val toAmount: BigDecimal? = null,
    val payee: String = "",
    val categoryId: Long? = null,
    val split: Boolean = false,
    val splits: List<SplitValue> = listOf(SplitValue(), SplitValue()),
    val tagNames: List<String> = emptyList(),
    val comment: String = "",
    val foreign: Boolean = false,
    val originalAmountText: String = "",
    val originalAmount: BigDecimal? = null,
    val originalCurrency: String = "USD",
    val adjustment: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val payees: List<Payee> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val baseCurrency: String = "UAH",
    val saving: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val validated: Boolean = false,
    val dirty: Boolean = false,
    val notFound: Boolean = false,
) {
    val isNew: Boolean get() = editing == null
    val imported: Boolean get() = editing?.isImported == true
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val toAccount: Account? get() = accounts.firstOrNull { it.id == toAccountId }
    val currency: String get() = account?.currency?.code ?: baseCurrency
    val categorized: Boolean get() = type == FormType.EXPENSE || type == FormType.INCOME
    val debtAccount: Account? get() = accounts.firstOrNull { it.type == AccountType.DEBT && !it.archived }

    /** The currency the other side of a transfer or a debt is in. */
    val toCurrency: String? get() = when (type) {
        FormType.TRANSFER -> toAccount?.currency?.code
        FormType.DEBT -> debtAccount?.currency?.code ?: baseCurrency
        else -> null
    }
    val currenciesDiffer: Boolean get() = toCurrency != null && toCurrency != currency

    val amountMissing: Boolean get() = validated && (amount == null || amount.signum() <= 0)
    val accountMissing: Boolean get() = validated && accountId == null
    val toAccountInvalid: Boolean
        get() = validated && type == FormType.TRANSFER && (toAccountId == null || toAccountId == accountId)
    val payeeMissing: Boolean get() = validated && type == FormType.DEBT && payee.isBlank()

    /** Unassigned part of a split: the amount minus the parts. */
    val splitRemainder: BigDecimal
        get() = (amount ?: BigDecimal.ZERO) -
            splits.fold(BigDecimal.ZERO) { sum, s -> sum + (s.amount ?: BigDecimal.ZERO) }
    val splitMismatch: Boolean get() = validated && split && categorized && splitRemainder.signum() != 0

    /** Anything the form shows in red after a submit attempt. */
    val hasErrors: Boolean
        get() = amountMissing || accountMissing || toAccountInvalid || payeeMissing || splitMismatch

    val categoriesOfKind: List<Category>
        get() {
            val kind = if (type == FormType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            return categories.filter { it.kind == kind }
        }

    /** Accounts for the pickers: most used first, archived only when selected (web `selectableAccounts`). */
    val selectableAccounts: List<Account>
        get() = accounts
            .filter { !it.archived || it.id == accountId || it.id == toAccountId }
            .filter { it.type != AccountType.DEBT || type != FormType.DEBT }
            .sortedWith(
                compareBy<Account> { it.archived }.thenByDescending { it.operations }.thenBy { it.name.lowercase() },
            )
}

/** The transaction editor (docs/04 §6.3 and Debts): the web's `form.ts` rules in a ViewModel. */
@Suppress("TooManyFunctions", "LargeClass")
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val transactions: TransactionsRepository,
    private val accounts: AccountsRepository,
    private val catalog: CatalogRepository,
    private val user: UserRepository,
    private val clock: KyivClock,
) : ViewModel() {

    private val state = MutableStateFlow(EditorUiState(date = clock.today()))
    val uiState: StateFlow<EditorUiState> = state.asStateFlow()

    fun load(id: Long?, type: TransactionType?, accountId: Long?, categoryId: Long?, payeeId: Long?) {
        if (state.value.loaded) return
        viewModelScope.launch {
            val accountList = accounts.accounts.first().dataOrNull.orEmpty()
            val categories = catalog.categories.first().dataOrNull.orEmpty()
            val payees = catalog.payees.first().dataOrNull.orEmpty()
            val tags = catalog.tags.first().dataOrNull.orEmpty()
            val base = user.user.first().dataOrNull?.baseCurrency?.code ?: "UAH"
            val catalogState = state.value.copy(
                accounts = accountList,
                categories = categories,
                payees = payees,
                tags = tags,
                baseCurrency = base,
            )
            if (id == null) {
                val defaultAccount = accountId ?: accountList.filter { !it.archived }.maxByOrNull { it.operations }?.id
                state.value = catalogState.copy(
                    loaded = true,
                    type = type?.let { FormType.valueOf(it.name) } ?: FormType.EXPENSE,
                    accountId = defaultAccount,
                    categoryId = categoryId,
                    payee = payeeId?.let { wanted -> payees.firstOrNull { it.id == wanted }?.name }.orEmpty(),
                )
                return@launch
            }
            val transaction = transactions.transaction(id).first {
                it !is org.andrewdev.webwallet.core.data.CacheState.Missing || !it.loading
            }.dataOrNull
            if (transaction == null) {
                state.value = catalogState.copy(loaded = true, notFound = true)
                return@launch
            }
            state.value = initialValues(catalogState, transaction)
        }
    }

    /** The web's `initialTransactionValues`: a transfer with a debt account opens as a debt. */
    @Suppress("CyclomaticComplexMethod")
    private fun initialValues(base: EditorUiState, transaction: Transaction): EditorUiState {
        val debtIds = base.accounts.filter { it.type == AccountType.DEBT }.map { it.id }.toSet()
        val direction = debtDirection(transaction, debtIds)
        val gave = direction == DebtDirection.GAVE
        val split = transaction.splits.size > 1
        val amount = if (direction != null && !gave) transaction.toAmount ?: transaction.amount else transaction.amount
        val toAmount = if (direction != null && !gave) transaction.amount else transaction.toAmount
        val tagNames = base.tags.associateBy { it.id }
        return base.copy(
            loaded = true,
            editing = transaction,
            type = direction?.let { FormType.DEBT } ?: FormType.valueOf(transaction.type.name),
            debtDirection = direction ?: DebtDirection.GAVE,
            amountText = amount.toPlainString(),
            amount = amount,
            date = transaction.date,
            accountId = if (direction != null && !gave) transaction.toAccountId else transaction.accountId,
            toAccountId = if (direction == null) transaction.toAccountId else null,
            toAmountText = toAmount?.takeIf { it != amount }?.toPlainString().orEmpty(),
            toAmount = toAmount?.takeIf { it != amount },
            payee = transaction.payee?.name.orEmpty(),
            categoryId = if (!split) transaction.splits.singleOrNull()?.categoryId ?: transaction.categoryId else null,
            split = split,
            splits = if (split) {
                transaction.splits.map {
                    SplitValue(
                        it.categoryId,
                        it.amount.toPlainString(),
                        it.amount,
                        it.comment.orEmpty(),
                    )
                }
            } else {
                listOf(SplitValue(), SplitValue())
            },
            tagNames = transaction.tagIds.mapNotNull { tagNames[it]?.name },
            comment = transaction.comment.orEmpty(),
            foreign = transaction.originalAmount != null,
            originalAmountText = transaction.originalAmount?.toPlainString().orEmpty(),
            originalAmount = transaction.originalAmount,
            originalCurrency = transaction.originalCurrency?.code ?: "USD",
            adjustment = transaction.adjustment,
        )
    }

    fun onTypeChange(type: FormType) = state.update { current ->
        var next = current.copy(type = type, dirty = true, error = null)
        // Categories of the other kind are dropped (web `switchType`)
        if ((type == FormType.INCOME) != (current.type == FormType.INCOME)) {
            next = next.copy(categoryId = null, splits = current.splits.map { it.copy(categoryId = null) })
        }
        val incomeToTransfer = current.type == FormType.INCOME && type == FormType.TRANSFER
        val transferToIncome =
            current.type == FormType.TRANSFER && type == FormType.INCOME && current.toAccountId != null
        if (incomeToTransfer || transferToIncome) {
            next = next.copy(accountId = current.toAccountId, toAccountId = current.accountId)
        }
        if (type == FormType.DEBT) next = next.copy(toAccountId = null)
        next
    }

    fun onDebtDirectionChange(direction: DebtDirection) = state.update {
        it.copy(
            debtDirection = direction,
            dirty = true,
        )
    }

    fun onAmountChange(text: String, value: BigDecimal?) = state.update {
        it.copy(amountText = text, amount = value, dirty = true, fieldErrors = it.fieldErrors - "amount")
    }

    fun onDateChange(date: LocalDate) = state.update { it.copy(date = date, dirty = true) }

    fun onAccountChange(id: Long) = state.update {
        it.copy(accountId = id, dirty = true, fieldErrors = it.fieldErrors - "accountId")
    }

    fun onToAccountChange(id: Long) = state.update {
        it.copy(toAccountId = id, dirty = true, fieldErrors = it.fieldErrors - "toAccountId")
    }

    fun swapAccounts() = state.update { current ->
        val amountsToo = current.toAmount != null
        current.copy(
            accountId = current.toAccountId,
            toAccountId = current.accountId,
            amountText = if (amountsToo) current.toAmountText else current.amountText,
            amount = if (amountsToo) current.toAmount else current.amount,
            toAmountText = if (amountsToo) current.amountText else current.toAmountText,
            toAmount = if (amountsToo) current.amount else current.toAmount,
            dirty = true,
        )
    }

    fun onToAmountChange(text: String, value: BigDecimal?) = state.update {
        it.copy(toAmountText = text, toAmount = value, dirty = true)
    }

    fun onPayeeChange(value: String) = state.update { it.copy(payee = value, dirty = true) }

    fun onCategoryChange(id: Long?) = state.update { it.copy(categoryId = id, dirty = true) }

    fun onSplitChange(split: Boolean) = state.update { it.copy(split = split, dirty = true) }

    fun onSplitRowChange(index: Int, row: SplitValue) = state.update {
        it.copy(splits = it.splits.toMutableList().also { list -> list[index] = row }, dirty = true)
    }

    fun addSplitRow() = state.update { it.copy(splits = it.splits + SplitValue(), dirty = true) }

    fun removeSplitRow(index: Int) = state.update {
        if (it.splits.size <= 2) it else it.copy(splits = it.splits.filterIndexed { i, _ -> i != index }, dirty = true)
    }

    /** "Distribute remainder": the unassigned part goes to the last row. */
    fun distributeRemainder() = state.update { current ->
        val remainder = current.splitRemainder
        val last = current.splits.lastIndex
        if (last < 0 || remainder.signum() == 0) return@update current
        val row = current.splits[last]
        val amount = (row.amount ?: BigDecimal.ZERO) + remainder
        onSplitRowChange(last, row.copy(amountText = amount.toPlainString(), amount = amount))
        state.value
    }

    fun onTagsChange(names: List<String>) = state.update { it.copy(tagNames = names, dirty = true) }

    fun onCommentChange(value: String) = state.update { it.copy(comment = value, dirty = true) }

    fun onForeignChange(foreign: Boolean) = state.update { it.copy(foreign = foreign, dirty = true) }

    fun onOriginalAmountChange(text: String, value: BigDecimal?) = state.update {
        it.copy(originalAmountText = text, originalAmount = value, dirty = true)
    }

    fun onOriginalCurrencyChange(code: String) = state.update { it.copy(originalCurrency = code, dirty = true) }

    @Suppress("CyclomaticComplexMethod")
    fun save(debtAccountName: String, onSaved: (Transaction) -> Unit) {
        val current = state.value.copy(validated = true)
        state.value = current
        if (current.hasErrors || current.saving) return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val tagIds = resolveTags(current.tagNames) ?: return@launch
            val draft = when (current.type) {
                FormType.DEBT -> {
                    val debtAccountId = ensureDebtAccount(current, debtAccountName) ?: return@launch
                    debtDraft(current, debtAccountId, tagIds)
                }
                else -> plainDraft(current, tagIds)
            }
            val result = current.editing?.let { transactions.update(it.id, draft) }
                ?: transactions.create(draft, current.creationKey)
            when (result) {
                is ApiResult.Ok -> onSaved(result.value)
                is ApiResult.Err -> fail(result.error)
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = state.value.editing?.id ?: return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = transactions.delete(id)) {
                is ApiResult.Ok -> onDeleted()
                is ApiResult.Err -> fail(result.error)
            }
        }
    }

    /** A merged bank transfer back into its two operations (docs/04 §6.3). */
    fun splitTransfer(onDone: () -> Unit) {
        val id = state.value.editing?.id ?: return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = transactions.splitTransfer(id)) {
                is ApiResult.Ok -> onDone()
                is ApiResult.Err -> fail(result.error)
            }
        }
    }

    private fun fail(error: ApiError) {
        val problem = error as? ApiError.Problem
        state.update {
            it.copy(
                saving = false,
                error = error,
                notFound = problem?.code == ErrorCode.NOT_FOUND,
                fieldErrors = problem?.fieldErrors.orEmpty().mapKeys { (key, _) ->
                    key.replace(
                        Regex("\\[(\\d+)]"),
                        ".$1",
                    )
                },
            )
        }
    }

    /** Tag names to ids; unknown names are created first (docs/04 §6.3). */
    private suspend fun resolveTags(names: List<String>): List<Long>? {
        val known = state.value.tags.associateBy { it.name.lowercase() }.toMutableMap()
        val ids = mutableListOf<Long>()
        for (name in names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }) {
            val existing = known[name.lowercase()]
            if (existing != null) {
                ids += existing.id
                continue
            }
            when (val created = catalog.createTag(name)) {
                is ApiResult.Ok -> {
                    known[name.lowercase()] = created.value
                    ids += created.value.id
                }
                is ApiResult.Err -> {
                    fail(created.error)
                    return null
                }
            }
        }
        return ids
    }

    /** The one debt account, created in the base currency on first use (web `ensureDebtAccount`). */
    private suspend fun ensureDebtAccount(current: EditorUiState, name: String): Long? {
        current.debtAccount?.let { return it.id }
        return when (
            val created = accounts.create(
                AccountDraft(name = name, type = AccountType.DEBT, currency = current.baseCurrency, inBudget = false),
                current.creationKey,
            )
        ) {
            is ApiResult.Ok -> created.value.id
            is ApiResult.Err -> {
                fail(created.error)
                null
            }
        }
    }

    private fun debtDraft(current: EditorUiState, debtAccountId: Long, tagIds: List<Long>): TransactionDraft {
        val amount = current.amount!!
        val gave = current.debtDirection == DebtDirection.GAVE
        val debtAmount = current.toAmount?.takeIf { current.currenciesDiffer } ?: amount
        return TransactionDraft(
            type = TransactionType.TRANSFER,
            date = current.date,
            accountId = if (gave) current.accountId!! else debtAccountId,
            amount = if (gave) amount else debtAmount,
            toAccountId = if (gave) debtAccountId else current.accountId!!,
            toAmount = if (gave) debtAmount else amount,
            payeeName = current.payee.trim(),
            tagIds = tagIds,
            comment = current.comment.trim(),
        )
    }

    private fun plainDraft(current: EditorUiState, tagIds: List<Long>): TransactionDraft {
        val transfer = current.type == FormType.TRANSFER
        val amount = current.amount!!
        return TransactionDraft(
            type = TransactionType.valueOf(current.type.name),
            date = current.date,
            accountId = current.accountId!!,
            amount = amount,
            toAccountId = if (transfer) current.toAccountId else null,
            // Always sent for transfers: an omitted amount would default to the sent one and drop a fee
            toAmount = if (transfer) current.toAmount ?: amount else null,
            payeeName = if (transfer) null else current.payee.trim(),
            categoryId = if (!transfer && !current.split) current.categoryId else null,
            splits = if (!transfer && current.split) {
                current.splits.map {
                    Split(null, it.amount ?: BigDecimal.ZERO, it.categoryId, it.comment.trim().ifEmpty { null })
                }
            } else {
                emptyList()
            },
            tagIds = tagIds,
            comment = current.comment.trim(),
            originalAmount = if (current.foreign) current.originalAmount else null,
            originalCurrency = current.originalCurrency.takeIf { current.foreign && current.originalAmount != null },
            adjustment = current.adjustment,
        )
    }
}
