package org.andrewdev.webwallet.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.CacheState
import org.andrewdev.webwallet.core.data.OverviewRepository
import org.andrewdev.webwallet.core.datastore.SettingsStore
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountGroup
import org.andrewdev.webwallet.core.model.AccountSummary
import org.andrewdev.webwallet.core.model.DebtPerson
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

/** A group of the accounts screen with its subtotal in the base currency (null when a rate is missing). */
data class AccountGroupRow(
    val group: AccountGroup,
    val accounts: List<Account>,
    val totalInBase: BigDecimal?,
    val collapsed: Boolean,
)

sealed interface AccountsUiState {
    data object Loading : AccountsUiState

    data class Error(val error: ApiError) : AccountsUiState

    data class Content(
        val summary: AccountSummary?,
        val groups: List<AccountGroupRow>,
        val debts: List<DebtPerson>,
        val baseCurrency: String,
        val showArchived: Boolean,
        val hasArchived: Boolean,
        /** Accounts whose balance differs from Monobank's, by id. */
        val differences: Map<Long, BigDecimal>,
        val monobankError: String?,
        val stale: Boolean,
        val fetchedAt: Instant?,
        val offline: Boolean,
        val refreshing: Boolean,
    ) : AccountsUiState {
        val isEmpty: Boolean get() = groups.isEmpty() && debts.isEmpty()
    }
}

/** The accounts list (docs/04 §5.1): groups like the web's `groups.ts`, archived at the end when shown. */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accounts: AccountsRepository,
    private val overview: OverviewRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        accounts.accounts,
        accounts.summary,
        overview.debts,
        overview.monobank,
        settings.data,
    ) { list, summary, debts, monobank, prefs ->
        when (list) {
            is CacheState.Missing -> {
                val error = list.error
                if (error != null && !list.loading) AccountsUiState.Error(error) else AccountsUiState.Loading
            }
            else -> {
                val all = list.dataOrNull.orEmpty()
                val status = monobank.dataOrNull
                AccountsUiState.Content(
                    summary = summary.dataOrNull,
                    baseCurrency = summary.dataOrNull?.currency?.code ?: "UAH",
                    groups = groupAccounts(all, prefs.showArchivedAccounts, prefs.collapsedAccountGroups),
                    debts = debts.dataOrNull.orEmpty(),
                    showArchived = prefs.showArchivedAccounts,
                    hasArchived = all.any { it.archived },
                    differences = status?.differences?.associate { it.accountId to it.difference }.orEmpty(),
                    monobankError = status?.lastError,
                    stale = list is CacheState.Stale,
                    fetchedAt = (list as? CacheState.Stale)?.fetchedAt,
                    offline = list.errorOrNull is ApiError.Network,
                    refreshing = list.isRefreshing,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountsUiState.Loading)

    fun refresh() {
        accounts.invalidate()
        overview.invalidate()
    }

    fun toggleArchived() {
        viewModelScope.launch { settings.update { it.copy(showArchivedAccounts = !it.showArchivedAccounts) } }
    }

    fun toggleGroup(group: AccountGroup) {
        viewModelScope.launch {
            settings.update {
                val groups = it.collapsedAccountGroups
                val collapsed = if (group.name in groups) groups - group.name else groups + group.name
                it.copy(collapsedAccountGroups = collapsed)
            }
        }
    }

    fun setArchived(account: Account, archived: Boolean, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = accounts.setArchived(account, archived)
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    fun syncMonobank(onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = overview.syncMonobankNow()
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The web's `groupAccounts`: by group in order, archived last and only on request. */
        fun groupAccounts(all: List<Account>, showArchived: Boolean, collapsed: Set<String>): List<AccountGroupRow> {
            val active = all.filter { !it.archived }
            val bySort = compareBy<Account> { it.sortOrder }.thenBy { it.name.lowercase() }
            val groups = AccountGroup.entries.filter { it != AccountGroup.ARCHIVED }.mapNotNull { group ->
                val members = active.filter { it.type in group.types }.sortedWith(bySort)
                if (members.isEmpty()) {
                    null
                } else {
                    AccountGroupRow(
                        group,
                        members,
                        totalInBase(members),
                        group.name in collapsed,
                    )
                }
            }
            val archived = all.filter { it.archived }.sortedWith(bySort)
            return if (showArchived && archived.isNotEmpty()) {
                groups + AccountGroupRow(AccountGroup.ARCHIVED, archived, null, AccountGroup.ARCHIVED.name in collapsed)
            } else {
                groups
            }
        }

        private fun totalInBase(accounts: List<Account>): BigDecimal? {
            if (accounts.any { it.balanceInBase == null }) return null
            return accounts.fold(BigDecimal.ZERO) { sum, account -> sum + account.balanceInBase!! }
        }
    }
}
