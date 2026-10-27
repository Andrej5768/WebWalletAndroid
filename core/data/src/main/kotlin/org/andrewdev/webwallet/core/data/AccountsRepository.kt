package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.andrewdev.webwallet.core.database.AccountDao
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountSummary
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.AccountsApi
import org.andrewdev.webwallet.core.network.generated.model.AccountRequest
import org.andrewdev.webwallet.core.network.map
import org.andrewdev.webwallet.core.network.onOk
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The fields of the account form; nulls mean "leave as is" on an update, as the backend's partial PUT does. */
data class AccountDraft(
    val name: String,
    val type: AccountType,
    val currency: String? = null,
    val openingBalance: BigDecimal? = null,
    val creditLimit: BigDecimal? = null,
    val interestRate: BigDecimal? = null,
    val inBudget: Boolean? = null,
    val archived: Boolean? = null,
)

@Singleton
class AccountsRepository @Inject constructor(
    private val api: AccountsApi,
    private val dao: AccountDao,
    private val jsonCache: JsonCache,
    private val cache: Cache,
) {
    /** Set once a list was stored, so an empty list is a real "no accounts" rather than "nothing cached". */
    private val fetched = MutableStateFlow(false)

    val accounts: Flow<CacheState<List<Account>>> = cache.observe(
        key = KEY_ACCOUNTS,
        ttl = Cache.SHORT,
        local = combine(
            dao.observeAll(),
            fetched,
        ) { rows, done -> rows.map { it.toModel() }.takeIf { rows.isNotEmpty() || done } },
        fetch = ::fetchAccounts,
    )

    val summary: Flow<CacheState<AccountSummary>> = cache.observe(
        key = KEY_SUMMARY,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_SUMMARY, SummaryJson.serializer()).map { it?.toModel() },
        fetch = {
            apiCall { api.summary() }.onOk {
                jsonCache.put(
                    KEY_SUMMARY,
                    SummaryJson.serializer(),
                    SummaryJson.of(it),
                )
            }
        },
    )

    suspend fun refresh() = fetchAccounts()

    fun invalidate() = cache.invalidate(KEY_ACCOUNTS, KEY_SUMMARY)

    /** [key] names the attempt: a retry with the same key gets the first response back instead of a duplicate. */
    suspend fun create(draft: AccountDraft, key: UUID): ApiResult<Account> =
        apiCall { api.create8(draft.toRequest(), key) }.map { it.toEntity().toModel() }.onOk { invalidate() }

    suspend fun update(id: Long, draft: AccountDraft): ApiResult<Account> =
        apiCall { api.update7(id, draft.toRequest()) }.map { it.toEntity().toModel() }.onOk { invalidate() }

    suspend fun setArchived(account: Account, archived: Boolean): ApiResult<Account> =
        update(account.id, AccountDraft(name = account.name, type = account.type, archived = archived))

    suspend fun delete(id: Long): ApiResult<Unit> = apiCall { api.delete8(id) }.onOk {
        dao.delete(id)
        invalidate()
    }

    private suspend fun fetchAccounts(): ApiResult<Unit> = apiCall { api.list6() }.map { list ->
        dao.replaceAll(list.map { it.toEntity() })
        fetched.value = true
    }

    private fun AccountDraft.toRequest() = AccountRequest(
        name = name,
        type = AccountRequest.Type.valueOf(type.name),
        currency = currency,
        openingBalance = openingBalance,
        creditLimit = creditLimit,
        interestRate = interestRate,
        inBudget = inBudget,
        archived = archived,
    )

    companion object {
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_SUMMARY = "accounts:summary"
    }
}
