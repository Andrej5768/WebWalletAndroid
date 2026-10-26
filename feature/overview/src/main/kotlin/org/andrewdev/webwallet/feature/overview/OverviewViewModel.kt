package org.andrewdev.webwallet.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.BankDifference
import org.andrewdev.webwallet.core.data.CacheState
import org.andrewdev.webwallet.core.data.CatalogRepository
import org.andrewdev.webwallet.core.data.FeedFilter
import org.andrewdev.webwallet.core.data.MonthFigures
import org.andrewdev.webwallet.core.data.OverviewRepository
import org.andrewdev.webwallet.core.data.TransactionsRepository
import org.andrewdev.webwallet.core.datastore.SettingsStore
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountSummary
import org.andrewdev.webwallet.core.model.AppConfig
import org.andrewdev.webwallet.core.model.BalanceMode
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.PeriodPreset
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.time.Instant
import javax.inject.Inject

sealed interface OverviewUiState {
    data object Loading : OverviewUiState

    data class Error(val error: ApiError) : OverviewUiState

    data class Content(
        val summary: AccountSummary?,
        val balanceMode: BalanceMode,
        val month: MonthFigures?,
        val recent: List<Transaction>,
        val accountsById: Map<Long, Account>,
        val categoriesById: Map<Long, Category>,
        val differences: List<BankDifference>,
        val overdueCount: Int,
        val updateAvailable: AppConfig?,
        val hasAccounts: Boolean,
        val stale: Boolean,
        val fetchedAt: Instant?,
        val offline: Boolean,
        val refreshing: Boolean,
    ) : OverviewUiState
}

/** The overview of M1 (docs/04 §4): the hero figure, this month, the alerts and the recent transactions. */
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val accounts: AccountsRepository,
    private val overview: OverviewRepository,
    private val transactions: TransactionsRepository,
    private val catalog: CatalogRepository,
    private val settings: SettingsStore,
    private val clock: KyivClock,
    private val versionCode: VersionCode,
) : ViewModel() {

    private val overdue = MutableStateFlow(0)

    private val recentFilter = FeedFilter(period = Period.of(PeriodPreset.MONTH, clock.today()))

    val uiState: StateFlow<OverviewUiState> = combine(
        accounts.summary,
        accounts.accounts,
        overview.monthFigures,
        transactions.feed(recentFilter),
        combine(catalog.categories, overview.monobank, settings.data, overdue) { c, m, s, o -> Extra(c, m, s, o) },
    ) { summary, accountList, month, recent, extra ->
        if (summary is CacheState.Missing && accountList is CacheState.Missing) {
            val error = summary.error ?: accountList.error
            if (error != null && !summary.loading) OverviewUiState.Error(error) else OverviewUiState.Loading
        } else {
            val config = extra.settings.appConfig
            OverviewUiState.Content(
                summary = summary.dataOrNull,
                balanceMode = extra.settings.balanceMode,
                month = month.dataOrNull,
                recent = recent.dataOrNull?.transactions.orEmpty().take(RECENT_COUNT),
                accountsById = accountList.dataOrNull.orEmpty().associateBy { it.id },
                categoriesById = extra.categories.dataOrNull.orEmpty().flatMap { it.flatten() }.associateBy { it.id },
                differences = extra.monobank.dataOrNull?.differences.orEmpty(),
                overdueCount = extra.overdue,
                updateAvailable = config?.takeIf { it.latestVersionCode > versionCode.value }?.let {
                    AppConfig(
                        it.minSupportedVersionCode,
                        it.latestVersionCode,
                        it.storeUrl,
                        it.googleSignIn,
                        it.marketSearch,
                        it.telegram,
                        it.push,
                    )
                },
                hasAccounts = accountList.dataOrNull?.isNotEmpty() ?: true,
                stale = summary is CacheState.Stale,
                fetchedAt = (summary as? CacheState.Stale)?.fetchedAt,
                offline = summary.errorOrNull is ApiError.Network,
                refreshing = summary.isRefreshing || accountList.isRefreshing,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OverviewUiState.Loading)

    init {
        viewModelScope.launch { overdue.value = (overview.overdueCount() as? ApiResult.Ok)?.value ?: 0 }
    }

    fun refresh() {
        accounts.invalidate()
        overview.invalidate()
        transactions.invalidateAll()
        viewModelScope.launch { overdue.value = (overview.overdueCount() as? ApiResult.Ok)?.value ?: 0 }
    }

    fun setBalanceMode(mode: BalanceMode) {
        viewModelScope.launch { settings.update { it.copy(balanceMode = mode) } }
    }

    private class Extra(
        val categories: CacheState<List<Category>>,
        val monobank: CacheState<org.andrewdev.webwallet.core.data.MonobankStatus>,
        val settings: org.andrewdev.webwallet.core.datastore.SettingsData,
        val overdue: Int,
    )

    companion object {
        private const val RECENT_COUNT = 6
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The versionCode of this build, provided by the app module so the overview can compare it with B8. */
data class VersionCode(val value: Int)
