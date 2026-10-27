package org.andrewdev.webwallet.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.SessionManager
import org.andrewdev.webwallet.core.data.AccountDataRepository
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.CacheState
import org.andrewdev.webwallet.core.data.NotificationsRepository
import org.andrewdev.webwallet.core.data.UserRepository
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.NotificationSettings
import org.andrewdev.webwallet.core.model.TelegramStatus
import org.andrewdev.webwallet.core.model.User
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.time.OffsetDateTime
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

// --- notifications

data class NotificationsUiState(
    val settings: NotificationSettings? = null,
    val telegram: TelegramStatus? = null,
    val accounts: List<Account> = emptyList(),
    val user: User? = null,
    val loading: Boolean = true,
    val error: ApiError? = null,
    /** The link to the bot was opened and the chat is not connected yet: the status is polled. */
    val linkPending: Boolean = false,
    val saving: Boolean = false,
)

/** The notification switches, the hour and the Telegram chat (docs/04 §14 Notifications). */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notifications: NotificationsRepository,
    accounts: AccountsRepository,
    user: UserRepository,
) : ViewModel() {

    private val pending = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)

    val uiState: StateFlow<NotificationsUiState> = combine(
        notifications.settings,
        notifications.telegram,
        combine(accounts.accounts, user.user) { a, u -> a to u },
        combine(pending, saving) { p, s -> p to s },
    ) { settings, telegram, (accountState, userState), (isPending, isSaving) ->
        NotificationsUiState(
            settings = settings.dataOrNull,
            telegram = telegram.dataOrNull,
            accounts = accountState.dataOrNull.orEmpty().filter { !it.archived },
            user = userState.dataOrNull,
            loading = settings is CacheState.Missing && settings.loading,
            error = (settings as? CacheState.Missing)?.error,
            linkPending = isPending && telegram.dataOrNull?.linked != true,
            saving = isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotificationsUiState())

    fun refresh() = notifications.invalidate()

    /** Every switch and the hour save at once, like the web. */
    fun save(transform: (NotificationSettings) -> NotificationSettings, onError: (ApiError) -> Unit) {
        val current = uiState.value.settings ?: return
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val result = notifications.update(transform(current))
            saving.value = false
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    /** Asks for the one-time link, hands it to the caller to open and polls the status while the chat connects. */
    fun link(password: String?, code: String?, onResult: (ApiResult<String>) -> Unit) {
        viewModelScope.launch {
            val result = notifications.link(password, code)
            onResult(result)
            if (result is ApiResult.Ok) {
                pending.value = true
                repeat(POLLS) {
                    delay(POLL_MS)
                    if (!pending.value) return@launch
                    notifications.refreshTelegram()
                    if (notifications.telegram.first().dataOrNull?.linked == true) {
                        pending.value = false
                        return@launch
                    }
                }
                pending.value = false
            }
        }
    }

    fun unlink(onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = notifications.unlink()
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    fun setQuickEntryAccount(accountId: Long?, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = notifications.setQuickEntryAccount(accountId)
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    companion object {
        private const val POLL_MS = 3_000L
        private const val POLLS = 300
    }
}

// --- data: export, start over, delete the account

data class DataUiState(
    val user: User? = null,
    val exporting: Boolean = false,
    val resetting: Boolean = false,
    val deleting: Boolean = false,
    val error: ApiError? = null,
    /** Set once the deletion is scheduled; the screen says so and the session ends. */
    val deletionScheduledAt: OffsetDateTime? = null,
)

/** Export, start over and the deletion of the account (docs/04 §14 Data). */
@HiltViewModel
class DataViewModel @Inject constructor(
    private val data: AccountDataRepository,
    private val session: SessionManager,
    user: UserRepository,
) : ViewModel() {

    private val state = MutableStateFlow(DataUiState())

    val uiState: StateFlow<DataUiState> = combine(state, user.user) { s, u -> s.copy(user = u.dataOrNull) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DataUiState())

    /** Downloads the archive; the caller writes the bytes where the user chose. */
    fun export(onResult: (ApiResult<ByteArray>) -> Unit) {
        if (state.value.exporting) return
        state.update { it.copy(exporting = true, error = null) }
        viewModelScope.launch {
            val result = data.export()
            state.update { it.copy(exporting = false, error = (result as? ApiResult.Err)?.error) }
            onResult(result)
        }
    }

    fun reset(confirm: String, keepCategories: Boolean, onDone: () -> Unit) {
        if (state.value.resetting) return
        state.update { it.copy(resetting = true, error = null) }
        viewModelScope.launch {
            when (val result = data.reset(confirm, keepCategories)) {
                is ApiResult.Ok -> {
                    state.update { it.copy(resetting = false) }
                    onDone()
                }
                is ApiResult.Err -> state.update { it.copy(resetting = false, error = result.error) }
            }
        }
    }

    fun deleteAccount(confirmEmail: String, password: String?, code: String?) {
        if (state.value.deleting) return
        state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            when (val result = data.deleteAccount(confirmEmail, password, code)) {
                is ApiResult.Ok -> state.update { it.copy(deleting = false, deletionScheduledAt = result.value) }
                is ApiResult.Err -> state.update { it.copy(deleting = false, error = result.error) }
            }
        }
    }

    /** After the deletion notice: the server already ended the session. */
    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    fun clearError() = state.update { it.copy(error = null) }
}
