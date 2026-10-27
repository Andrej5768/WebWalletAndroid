package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.andrewdev.webwallet.core.model.Named
import org.andrewdev.webwallet.core.model.NotificationSettings
import org.andrewdev.webwallet.core.model.Rule
import org.andrewdev.webwallet.core.model.RuleDraft
import org.andrewdev.webwallet.core.model.TelegramStatus
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.CategorizationRulesApi
import org.andrewdev.webwallet.core.network.generated.api.CurrentUserApi
import org.andrewdev.webwallet.core.network.generated.api.NotificationsApi
import org.andrewdev.webwallet.core.network.generated.model.ConfirmIdentityRequest
import org.andrewdev.webwallet.core.network.generated.model.DeleteAccountRequest
import org.andrewdev.webwallet.core.network.generated.model.NotificationSettingsDto
import org.andrewdev.webwallet.core.network.generated.model.ResetRequest
import org.andrewdev.webwallet.core.network.generated.model.RuleRequest
import org.andrewdev.webwallet.core.network.generated.model.RuleResponse
import org.andrewdev.webwallet.core.network.generated.model.TelegramSettingsRequest
import org.andrewdev.webwallet.core.network.map
import org.andrewdev.webwallet.core.network.onOk
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// --- categorization rules

@Serializable
internal data class RuleJson(
    val id: Long,
    val categoryId: Long,
    val payee: NamedJson?,
    val payeePattern: String?,
    val mcc: Int?,
    val bankCategory: String?,
    val learned: Boolean,
)

@Serializable
internal data class RulesJson(val items: List<RuleJson>)

/** The categorization rules (docs/04 §14 Rules), cached whole. */
@Singleton
class RulesRepository @Inject constructor(
    private val api: CategorizationRulesApi,
    private val transactions: TransactionsRepository,
    private val jsonCache: JsonCache,
    private val cache: Cache,
) {
    val rules: Flow<CacheState<List<Rule>>> = cache.observe(
        key = KEY,
        ttl = Cache.LONG,
        local = jsonCache.observe(KEY, RulesJson.serializer()).map { json -> json?.items?.map { it.toModel() } },
        fetch = {
            apiCall { api.list5() }.map { list ->
                jsonCache.put(KEY, RulesJson.serializer(), RulesJson(list.map { it.toJson() }))
            }
        },
    )

    suspend fun create(draft: RuleDraft, key: UUID): ApiResult<Rule> =
        apiCall { api.create6(draft.toRequest(), key) }.map { it.toJson().toModel() }.onOk { invalidate() }

    suspend fun update(id: Long, draft: RuleDraft): ApiResult<Rule> =
        apiCall { api.update5(id, draft.toRequest()) }.map { it.toJson().toModel() }.onOk { invalidate() }

    suspend fun delete(id: Long): ApiResult<Unit> = apiCall { api.delete6(id) }.onOk { invalidate() }

    /** Puts the uncategorized transactions into categories by the rules; returns how many. */
    suspend fun apply(): ApiResult<Int> = apiCall { api.apply() }.map { it.categorized }.onOk {
        transactions.invalidateAll()
    }

    fun invalidate() = cache.invalidate(KEY)

    private fun RuleDraft.toRequest() = RuleRequest(
        categoryId = categoryId,
        bankCategory = bankCategory?.trim()?.ifEmpty { null },
        mcc = mcc,
        payeeId = payeeId,
        payeePattern = payeePattern?.trim()?.ifEmpty { null },
    )

    private fun RuleResponse.toJson() =
        RuleJson(id, categoryId, payee?.let { NamedJson(it.id, it.name) }, payeePattern, mcc, bankCategory, learned)

    companion object {
        const val KEY = "rules"
    }
}

internal fun RuleJson.toModel() =
    Rule(id, categoryId, payee?.let { Named(it.id, it.name) }, payeePattern, mcc, bankCategory, learned)

// --- notifications and Telegram

@Serializable
internal data class NotificationSettingsJson(
    val recurring: Boolean,
    val budgets: Boolean,
    val taxes: Boolean,
    val banks: Boolean,
    val forecast: Boolean,
    val goals: Boolean,
    val weekly: Boolean,
    val monthly: Boolean,
    val emailWeekly: Boolean,
    val emailMonthly: Boolean,
    val hour: Int,
)

@Serializable
internal data class TelegramJson(
    val enabled: Boolean,
    val botUsername: String?,
    val linked: Boolean,
    val firstName: String?,
    val username: String?,
    val linkedAt: String?,
    val quickEntryAccountId: Long?,
)

/** The notification switches and the Telegram chat (docs/04 §14 Notifications, docs/06 §1.4). */
@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
    private val jsonCache: JsonCache,
    private val cache: Cache,
) {
    val settings: Flow<CacheState<NotificationSettings>> = cache.observe(
        key = KEY_SETTINGS,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_SETTINGS, NotificationSettingsJson.serializer()).map { it?.toModel() },
        fetch = { apiCall { api.get2() }.map { store(it) } },
    )

    val telegram: Flow<CacheState<TelegramStatus>> = cache.observe(
        key = KEY_TELEGRAM,
        ttl = Cache.SHORT,
        local = jsonCache.observe(KEY_TELEGRAM, TelegramJson.serializer()).map { it?.toModel() },
        fetch = { apiCall { api.status1() }.map { store(it) } },
    )

    suspend fun update(settings: NotificationSettings): ApiResult<NotificationSettings> = apiCall {
        api.update2(
            NotificationSettingsDto(
                banks = settings.banks,
                budgets = settings.budgets,
                emailMonthly = settings.emailMonthly,
                emailWeekly = settings.emailWeekly,
                forecast = settings.forecast,
                goals = settings.goals,
                hour = settings.hour,
                monthly = settings.monthly,
                recurring = settings.recurring,
                taxes = settings.taxes,
                weekly = settings.weekly,
            ),
        )
    }.map {
        cache.markFresh(KEY_SETTINGS)
        store(it)
        it.toModel()
    }

    /** A one-time link to the bot after the identity check; the status is polled until the chat is connected. */
    suspend fun link(password: String?, code: String?): ApiResult<String> =
        apiCall { api.link1(ConfirmIdentityRequest(code = code, password = password)) }.map { it.url }

    suspend fun unlink(): ApiResult<Unit> = apiCall { api.unlink1() }.onOk { cache.invalidate(KEY_TELEGRAM) }

    suspend fun setQuickEntryAccount(accountId: Long?): ApiResult<TelegramStatus> =
        apiCall { api.updateSettings(TelegramSettingsRequest(accountId)) }.map {
            cache.markFresh(KEY_TELEGRAM)
            store(it)
            it.toModel()
        }

    fun refreshTelegram() = cache.invalidate(KEY_TELEGRAM)

    fun invalidate() = cache.invalidate(KEY_SETTINGS, KEY_TELEGRAM)

    private suspend fun store(dto: NotificationSettingsDto) {
        jsonCache.put(KEY_SETTINGS, NotificationSettingsJson.serializer(), dto.toJson())
    }

    private suspend fun store(status: org.andrewdev.webwallet.core.network.generated.model.TelegramStatus) {
        jsonCache.put(
            KEY_TELEGRAM,
            TelegramJson.serializer(),
            TelegramJson(
                status.enabled,
                status.botUsername,
                status.linked,
                status.firstName,
                status.username,
                status.linkedAt?.toString(),
                status.quickEntryAccountId,
            ),
        )
    }

    private fun NotificationSettingsDto.toJson() = NotificationSettingsJson(
        recurring = recurring ?: true,
        budgets = budgets ?: true,
        taxes = taxes ?: true,
        banks = banks ?: true,
        forecast = forecast ?: true,
        goals = goals ?: true,
        weekly = weekly ?: true,
        monthly = monthly ?: true,
        emailWeekly = emailWeekly ?: true,
        emailMonthly = emailMonthly ?: true,
        hour = hour ?: DEFAULT_HOUR,
    )

    private fun NotificationSettingsDto.toModel() = toJson().toModel()

    private fun org.andrewdev.webwallet.core.network.generated.model.TelegramStatus.toModel() =
        TelegramStatus(enabled, botUsername, linked, firstName, username, linkedAt, quickEntryAccountId)

    companion object {
        const val KEY_SETTINGS = "notifications:settings"
        const val KEY_TELEGRAM = "telegram"
        private const val DEFAULT_HOUR = 9
    }
}

internal fun NotificationSettingsJson.toModel() = NotificationSettings(
    recurring, budgets, taxes, banks, forecast, goals, weekly, monthly, emailWeekly, emailMonthly, hour,
)

internal fun TelegramJson.toModel() = TelegramStatus(
    enabled,
    botUsername,
    linked,
    firstName,
    username,
    linkedAt?.let(OffsetDateTime::parse),
    quickEntryAccountId,
)

// --- the data of the account

/** Export, start over and the deletion of the account (docs/04 §14 Data). */
@Singleton
class AccountDataRepository @Inject constructor(
    private val api: CurrentUserApi,
    private val transactions: TransactionsRepository,
    private val catalog: CatalogRepository,
    private val cache: Cache,
) {
    /** The ZIP archive with everything of the user. */
    suspend fun export(): ApiResult<ByteArray> = apiCall { api.export() }

    /** Deletes the accounts, transactions and the rest; the profile stays. [confirm] is the typed word. */
    suspend fun reset(confirm: String, keepCategories: Boolean): ApiResult<Unit> =
        apiCall { api.reset(ResetRequest(confirm = confirm, keepCategories = keepCategories)) }.onOk {
            cache.forgetAll()
            catalog.invalidate()
            transactions.invalidateAll()
        }

    /** Schedules the deletion in 14 days; the session ends on the server, the caller signs out. */
    suspend fun deleteAccount(confirmEmail: String, password: String?, code: String?): ApiResult<OffsetDateTime> =
        apiCall { api.delete9(DeleteAccountRequest(code = code, confirmEmail = confirmEmail, password = password)) }
            .map { it.deletionScheduledAt }
}
