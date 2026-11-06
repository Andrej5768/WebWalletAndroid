package org.andrewdev.webwallet.feature.imports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.ImportsRepository
import org.andrewdev.webwallet.core.data.SharedFileInbox
import org.andrewdev.webwallet.core.data.TransactionDraft
import org.andrewdev.webwallet.core.data.TransactionsRepository
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.DetectedSource
import org.andrewdev.webwallet.core.model.ImportPreview
import org.andrewdev.webwallet.core.model.MonobankConnection
import org.andrewdev.webwallet.core.model.MonobankLink
import org.andrewdev.webwallet.core.model.MonobankSchedule
import org.andrewdev.webwallet.core.model.ProviderAuth
import org.andrewdev.webwallet.core.model.ProviderAuthStatus
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.model.UploadFile
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** The connect sheet of Monobank: the form, or the wait for the approval in the bank app. */
sealed interface ConnectStep {
    data object Closed : ConnectStep

    /** [manual] shows the token field even when the bank app flow is available. */
    data class Form(val manual: Boolean = false) : ConnectStep

    data class Waiting(val auth: ProviderAuth) : ConnectStep

    data object Expired : ConnectStep
}

/** The file sources of the screen; each has its own form and its own progress. */
enum class FileSource { PRIVATBANK, MONOBANK_PDF, ZENMONEY, FREEDOM }

enum class ZenMode { TOKEN, FILE }

data class SourceForms(
    val privatFile: UploadFile? = null,
    val privatAccountId: Long? = null,
    val monoFile: UploadFile? = null,
    val monoAccountId: Long? = null,
    val zenMode: ZenMode = ZenMode.TOKEN,
    val zenToken: String = "",
    val zenFile: UploadFile? = null,
    val freedomTrades: UploadFile? = null,
    val freedomCash: UploadFile? = null,
    val freedomAccountId: Long? = null,
    /** The source whose preview is being prepared. */
    val busy: FileSource? = null,
    val error: Pair<FileSource, ApiError>? = null,
)

data class ImportsUiState(
    val loading: Boolean = true,
    val error: ApiError? = null,
    val providerAvailable: Boolean = false,
    val connection: MonobankConnection? = null,
    val accounts: List<Account> = emptyList(),
    /** The schedule being edited; null while it matches the saved one. */
    val scheduleDraft: MonobankSchedule? = null,
    val savingSchedule: Boolean = false,
    val connect: ConnectStep = ConnectStep.Closed,
    val connecting: Boolean = false,
    val connectError: ApiError? = null,
    val disconnecting: Boolean = false,
    val adjusting: Long? = null,
    val sources: SourceForms = SourceForms(),
    /** A file shared from another app is being recognized. */
    val detecting: Boolean = false,
    /** A shared file the server could not recognize: the user says what it is. */
    val unrecognized: UploadFile? = null,
    /** A preview is ready to open; the screen navigates and clears it. */
    val ready: PreviewReady? = null,
) {
    val schedule: MonobankSchedule get() = scheduleDraft ?: connection?.schedule ?: MonobankSchedule()
}

/** Where a finished preview goes. */
sealed interface PreviewReady {
    data class Bank(val id: String) : PreviewReady

    data class Broker(val id: String) : PreviewReady
}

/** The imports screen (docs/04 §13): the Monobank card and the file sources. */
@HiltViewModel
@Suppress("TooManyFunctions")
class ImportsViewModel @Inject constructor(
    private val imports: ImportsRepository,
    private val transactions: TransactionsRepository,
    private val clock: KyivClock,
    private val savedState: SavedStateHandle,
    private val inbox: SharedFileInbox,
    accounts: AccountsRepository,
) : ViewModel() {

    private val state = MutableStateFlow(ImportsUiState())
    private var pollJob: Job? = null
    private var syncJob: Job? = null

    val uiState: StateFlow<ImportsUiState> = combine(state, accounts.accounts) { s, a ->
        s.copy(accounts = a.dataOrNull.orEmpty().filter { !it.archived })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ImportsUiState())

    init {
        refresh()
        // An access request that was waiting when the process died: ask again after the return from the bank.
        savedState.get<Long>(KEY_AUTH)?.let { resumeAuth(it) }
    }

    fun refresh() {
        viewModelScope.launch {
            state.update { it.copy(loading = it.connection == null, error = null) }
            val provider = imports.monobankProviderAvailable()
            when (val result = imports.monobank()) {
                is ApiResult.Ok -> {
                    state.update { it.copy(loading = false, providerAvailable = provider, connection = result.value) }
                    watchSync(result.value)
                }
                is ApiResult.Err -> state.update {
                    it.copy(
                        loading = false,
                        providerAvailable = provider,
                        error = result.error,
                    )
                }
            }
        }
    }

    // --- connect

    fun openConnect() = state.update { it.copy(connect = ConnectStep.Form(), connectError = null) }

    fun toggleManual() = state.update {
        val form = it.connect as? ConnectStep.Form ?: ConnectStep.Form()
        it.copy(connect = form.copy(manual = !form.manual), connectError = null)
    }

    fun closeConnect() {
        pollJob?.cancel()
        savedState.remove<Long>(KEY_AUTH)
        state.update { it.copy(connect = ConnectStep.Closed, connecting = false, connectError = null) }
    }

    /** Through the bank app when the server offers it and the token was not asked for; otherwise with the token. */
    fun connect(token: String, since: LocalDate, schedule: MonobankSchedule, onAcceptUrl: (String) -> Unit) {
        val current = state.value
        if (current.connecting) return
        val manual = (current.connect as? ConnectStep.Form)?.manual == true
        state.update { it.copy(connecting = true, connectError = null) }
        viewModelScope.launch {
            if (current.providerAvailable && !manual) {
                when (val result = imports.startProviderAuth(since, schedule)) {
                    is ApiResult.Ok -> {
                        savedState[KEY_AUTH] = result.value.id
                        state.update { it.copy(connecting = false, connect = ConnectStep.Waiting(result.value)) }
                        onAcceptUrl(result.value.acceptUrl)
                        poll(result.value.id)
                    }
                    is ApiResult.Err -> state.update { it.copy(connecting = false, connectError = result.error) }
                }
            } else {
                when (val result = imports.connectWithToken(token, since, schedule)) {
                    is ApiResult.Ok -> connected(result.value)
                    is ApiResult.Err -> state.update { it.copy(connecting = false, connectError = result.error) }
                }
            }
        }
    }

    /** Back from the bank app: ask at once instead of waiting for the next tick. */
    /** Back from the bank app or from the app that shared a file. */
    fun onResume() {
        inbox.take()?.let { receive(it) }
        val waiting = state.value.connect as? ConnectStep.Waiting ?: return
        poll(waiting.auth.id)
    }

    /** A file shared into the app: recognized by the server, then previewed like one picked on the screen. */
    private fun receive(file: UploadFile) {
        state.update { it.copy(detecting = true, unrecognized = null) }
        viewModelScope.launch {
            val source = imports.detect(file)
            state.update { it.copy(detecting = false) }
            if (source == DetectedSource.UNKNOWN) {
                state.update { it.copy(unrecognized = file) }
            } else {
                importAs(file, source)
            }
        }
    }

    /** The user's answer for an unrecognized file; null dismisses it. */
    fun importAs(file: UploadFile, source: DetectedSource?) {
        state.update { it.copy(unrecognized = null) }
        when (source) {
            DetectedSource.PRIVATBANK -> {
                updateForms { it.copy(privatFile = file) }
                preview(FileSource.PRIVATBANK)
            }
            DetectedSource.MONOBANK -> {
                updateForms { it.copy(monoFile = file) }
                preview(FileSource.MONOBANK_PDF)
            }
            DetectedSource.ZENMONEY -> {
                updateForms { it.copy(zenMode = ZenMode.FILE, zenFile = file) }
                preview(FileSource.ZENMONEY)
            }
            // The broker needs both reports: the second one is added on the screen
            DetectedSource.FREEDOM -> updateForms {
                if (it.freedomTrades == null) it.copy(freedomTrades = file) else it.copy(freedomCash = file)
            }
            DetectedSource.UNKNOWN, null -> Unit
        }
    }

    fun consumeReady() = state.update { it.copy(ready = null) }

    fun onPause() {
        pollJob?.cancel()
    }

    private fun resumeAuth(id: Long) {
        viewModelScope.launch {
            val result = imports.providerAuthStatus(id)
            val auth = result.valueOrNull ?: return@launch
            handleAuth(auth)
            if (auth.status == ProviderAuthStatus.PENDING) poll(id)
        }
    }

    /** Asks every few seconds while the screen is in front; network errors keep the wait going. */
    private fun poll(id: Long) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val auth = imports.providerAuthStatus(id).valueOrNull
                if (auth != null && !handleAuth(auth)) return@launch
                delay(POLL_MS)
            }
        }
    }

    /** Applies a status; false once the wait is over. */
    private fun handleAuth(auth: ProviderAuth): Boolean = when (auth.status) {
        ProviderAuthStatus.PENDING -> {
            state.update { it.copy(connect = ConnectStep.Waiting(auth)) }
            true
        }
        ProviderAuthStatus.APPROVED -> {
            savedState.remove<Long>(KEY_AUTH)
            state.update { it.copy(connect = ConnectStep.Closed) }
            refresh()
            false
        }
        ProviderAuthStatus.EXPIRED -> {
            savedState.remove<Long>(KEY_AUTH)
            state.update { it.copy(connect = ConnectStep.Expired) }
            false
        }
    }

    private fun connected(connection: MonobankConnection) {
        state.update {
            it.copy(connecting = false, connect = ConnectStep.Closed, connection = connection, scheduleDraft = null)
        }
    }

    // --- connected card

    fun editSchedule(schedule: MonobankSchedule) = state.update {
        it.copy(scheduleDraft = schedule.takeIf { draft -> draft != it.connection?.schedule })
    }

    fun saveSchedule(onError: (ApiError) -> Unit) {
        val draft = state.value.scheduleDraft ?: return
        state.update { it.copy(savingSchedule = true) }
        viewModelScope.launch {
            when (val result = imports.saveSchedule(draft)) {
                is ApiResult.Ok -> state.update {
                    it.copy(savingSchedule = false, scheduleDraft = null, connection = result.value)
                }
                is ApiResult.Err -> {
                    state.update { it.copy(savingSchedule = false) }
                    onError(result.error)
                }
            }
        }
    }

    fun syncNow(onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            when (val result = imports.syncNow()) {
                is ApiResult.Ok -> {
                    state.update { it.copy(connection = result.value) }
                    watchSync(result.value)
                }
                is ApiResult.Err -> onError(result.error)
            }
        }
    }

    /** While a synchronization is due or running the status is asked again every few seconds, like the web. */
    private fun watchSync(connection: MonobankConnection?) {
        val due = connection?.nextSyncAt ?: return
        if (due.toInstant().isAfter(clock.now())) return
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            repeat(SYNC_POLLS) {
                delay(SYNC_POLL_MS)
                val next = imports.monobank().valueOrNull ?: return@repeat
                state.update { it.copy(connection = next) }
                val stillDue = next.nextSyncAt?.toInstant()?.isAfter(clock.now()) == false
                if (!stillDue) return@launch
            }
        }
    }

    fun link(link: MonobankLink, accountId: Long?, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            when (val result = imports.link(link.id, accountId)) {
                is ApiResult.Ok -> state.update { it.copy(connection = result.value) }
                is ApiResult.Err -> onError(result.error)
            }
        }
    }

    /** A balance correction of the difference: an operation without a category that the reports leave out. */
    fun adjust(link: MonobankLink, onError: (ApiError) -> Unit) {
        val accountId = link.accountId ?: return
        val difference = link.difference ?: return
        if (difference.signum() == 0) return
        state.update { it.copy(adjusting = link.id) }
        viewModelScope.launch {
            val draft = TransactionDraft(
                type = if (difference.signum() > 0) TransactionType.INCOME else TransactionType.EXPENSE,
                date = clock.today(),
                accountId = accountId,
                amount = difference.abs(),
                adjustment = true,
            )
            val result = transactions.create(draft, UUID.randomUUID())
            state.update { it.copy(adjusting = null) }
            when (result) {
                is ApiResult.Ok -> refresh()
                is ApiResult.Err -> onError(result.error)
            }
        }
    }

    fun disconnect(onError: (ApiError) -> Unit) {
        state.update { it.copy(disconnecting = true) }
        viewModelScope.launch {
            val result = imports.disconnect()
            state.update { it.copy(disconnecting = false) }
            when (result) {
                is ApiResult.Ok -> state.update { it.copy(connection = null, scheduleDraft = null) }
                is ApiResult.Err -> onError(result.error)
            }
        }
    }

    // --- files

    fun updateForms(transform: (SourceForms) -> SourceForms) = state.update {
        it.copy(sources = transform(it.sources).copy(error = null))
    }

    fun preview(source: FileSource) {
        val forms = state.value.sources
        if (forms.busy != null) return
        state.update { it.copy(sources = it.sources.copy(busy = source, error = null)) }
        viewModelScope.launch {
            val result = request(source, forms) ?: return@launch idle()
            when (result) {
                is ApiResult.Ok -> {
                    state.update {
                        it.copy(
                            sources = it.sources.copy(busy = null, zenToken = "").let { s -> clearFile(s, source) },
                            ready = result.value,
                        )
                    }
                }
                is ApiResult.Err -> state.update {
                    it.copy(sources = it.sources.copy(busy = null, error = source to result.error))
                }
            }
        }
    }

    /** The upload of the source's form; null when the form has no file. */
    private suspend fun request(source: FileSource, forms: SourceForms): ApiResult<PreviewReady>? {
        val bank = { preview: ImportPreview -> PreviewReady.Bank(preview.id) }
        return when (source) {
            FileSource.PRIVATBANK -> forms.privatFile?.let {
                imports.previewPrivatBank(it, forms.privatAccountId).toReady(bank)
            }
            FileSource.MONOBANK_PDF -> forms.monoFile?.let {
                imports.previewMonobankPdf(it, forms.monoAccountId).toReady(bank)
            }
            FileSource.ZENMONEY -> when (forms.zenMode) {
                ZenMode.TOKEN -> imports.previewZenMoneyToken(forms.zenToken).toReady(bank)
                ZenMode.FILE -> forms.zenFile?.let { imports.previewZenMoneyFile(it).toReady(bank) }
            }
            FileSource.FREEDOM -> listOfNotNull(
                forms.freedomTrades,
                forms.freedomCash,
            ).takeIf { it.isNotEmpty() }?.let {
                imports.previewFreedom(
                    it,
                    forms.freedomAccountId,
                ).toReady { preview -> PreviewReady.Broker(preview.id) }
            }
        }
    }

    private fun idle() = state.update { it.copy(sources = it.sources.copy(busy = null)) }

    private fun clearFile(forms: SourceForms, source: FileSource) = when (source) {
        FileSource.PRIVATBANK -> forms.copy(privatFile = null)
        FileSource.MONOBANK_PDF -> forms.copy(monoFile = null)
        FileSource.ZENMONEY -> forms.copy(zenFile = null)
        FileSource.FREEDOM -> forms.copy(freedomTrades = null, freedomCash = null)
    }

    private inline fun <T> ApiResult<T>.toReady(transform: (T) -> PreviewReady): ApiResult<PreviewReady> =
        when (this) {
            is ApiResult.Ok -> ApiResult.Ok(transform(value))
            is ApiResult.Err -> this
        }

    companion object {
        private const val KEY_AUTH = "monobankAuthId"
        private const val POLL_MS = 3_000L
        private const val SYNC_POLL_MS = 5_000L
        private const val SYNC_POLLS = 60
    }
}
