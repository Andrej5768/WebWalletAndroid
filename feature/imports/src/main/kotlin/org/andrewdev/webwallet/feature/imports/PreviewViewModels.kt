package org.andrewdev.webwallet.feature.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.data.AccountsRepository
import org.andrewdev.webwallet.core.data.CatalogRepository
import org.andrewdev.webwallet.core.data.ImportsRepository
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.BrokerImportResult
import org.andrewdev.webwallet.core.model.BrokerOperationStatus
import org.andrewdev.webwallet.core.model.BrokerPreview
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.ImportOperation
import org.andrewdev.webwallet.core.model.ImportPreview
import org.andrewdev.webwallet.core.model.ImportResult
import org.andrewdev.webwallet.core.model.Tag
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.ErrorCode
import java.util.UUID
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L
private const val HTTP_NOT_FOUND = 404

/** The preview is gone on the server (an hour passed) or with the process (the broker's). */
private fun ApiError.isExpired() = this is ApiError.Problem && (status == HTTP_NOT_FOUND || code == ErrorCode.NOT_FOUND)

enum class PreviewFilter { ALL, UNCATEGORIZED, DUPLICATES }

data class PreviewUiState(
    val loading: Boolean = true,
    val expired: Boolean = false,
    val error: ApiError? = null,
    val first: ImportPreview? = null,
    /** Operations of every loaded page, in order. */
    val operations: List<ImportOperation> = emptyList(),
    val pagesLoaded: Int = 0,
    val loadingMore: Boolean = false,
    val edits: PreviewEditState = PreviewEditState(),
    val filter: PreviewFilter = PreviewFilter.ALL,
    val selected: Set<String> = emptySet(),
    /** A confirm was tried with transfers that miss the other account: those rows are marked. */
    val showErrors: Boolean = false,
    val confirming: Boolean = false,
    val confirmError: ApiError? = null,
    val result: ImportResult? = null,
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val accounts: List<Account> = emptyList(),
) {
    val totalPages: Int get() = first?.totalPages ?: 0
    val hasMore: Boolean get() = pagesLoaded < totalPages

    val visible: List<ImportOperation>
        get() = operations.filter { operation ->
            when (filter) {
                PreviewFilter.ALL -> true
                PreviewFilter.DUPLICATES -> operation.duplicate
                PreviewFilter.UNCATEGORIZED ->
                    !operation.duplicate && PreviewEdits.isUncategorized(operation, edits.editOf(operation))
            }
        }

    val total: Long get() = first?.totalItems ?: 0
    val duplicates: Long get() = total - (first?.summary?.transactionsImported ?: 0)
    val importCount: Int get() = (first?.summary?.transactionsImported ?: 0) - edits.excludedCount
}

/** The preview of a bank statement or a Zen Money export (docs/04 §13.3). */
@HiltViewModel
@Suppress("TooManyFunctions")
class ImportPreviewViewModel @Inject constructor(
    private val imports: ImportsRepository,
    private val catalog: CatalogRepository,
    accounts: AccountsRepository,
) : ViewModel() {

    private val state = MutableStateFlow(PreviewUiState())
    private var id: String? = null

    val uiState: StateFlow<PreviewUiState> = combine(
        state,
        catalog.categories,
        catalog.tags,
        accounts.accounts,
    ) { s, c, t, a ->
        s.copy(
            categories = c.dataOrNull.orEmpty(),
            tags = t.dataOrNull.orEmpty(),
            accounts = a.dataOrNull.orEmpty().filter { !it.archived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PreviewUiState())

    init {
        // The suggested tags of a row arrive as ids; the rows show and send names.
        viewModelScope.launch {
            catalog.tags.collect { tags ->
                val names = tags.dataOrNull.orEmpty().associate { it.id to it.name }
                state.update { it.copy(edits = it.edits.copy(tagNames = names)) }
            }
        }
    }

    fun load(previewId: String) {
        if (id == previewId) return
        id = previewId
        loadPage(0)
    }

    /** After a failed first load the whole preview is asked again; after a failed page, that page. */
    fun retry() {
        if (state.value.first == null) loadPage(0) else loadMore()
    }

    fun loadMore() {
        val current = state.value
        if (current.loadingMore || !current.hasMore || current.loading) return
        loadPage(current.pagesLoaded)
    }

    private fun loadPage(page: Int) {
        val previewId = id ?: return
        state.update { if (page == 0) it.copy(loading = true, error = null) else it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val result = imports.previewPage(previewId, page)) {
                is ApiResult.Ok -> state.update {
                    val preview = result.value
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        first = it.first ?: preview,
                        operations = it.operations + preview.operations,
                        pagesLoaded = page + 1,
                        edits = it.edits.withOperations(preview.operations),
                    )
                }
                is ApiResult.Err -> state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        expired = result.error.isExpired(),
                        error = result.error.takeUnless { e -> e.isExpired() },
                    )
                }
            }
        }
    }

    fun setFilter(filter: PreviewFilter) = state.update { it.copy(filter = filter) }

    fun change(operation: ImportOperation, patch: (RowEdit) -> RowEdit) =
        state.update { it.copy(edits = it.edits.update(operation, patch)) }

    fun toggleSelected(key: String) = state.update {
        it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key)
    }

    fun selectVisible() = state.update { s ->
        s.copy(selected = s.selected + s.visible.filter { !it.duplicate }.map { it.key })
    }

    fun clearSelection() = state.update { it.copy(selected = emptySet()) }

    fun applyBulk(categoryId: Long?, tag: String?) = state.update {
        it.copy(edits = it.edits.applyBulk(it.selected, categoryId, tag), selected = emptySet())
    }

    /** Creates the tags that do not exist yet, then sends the changed rows. */
    fun confirm() {
        val previewId = id ?: return
        val current = uiState.value
        if (current.confirming) return
        if (current.edits.withoutTransferAccount > 0) {
            state.update { it.copy(showErrors = true) }
            return
        }
        state.update { it.copy(confirming = true, confirmError = null) }
        viewModelScope.launch {
            val ids = mutableMapOf<String, Long>()
            current.tags.forEach { ids[it.name.trim().lowercase()] = it.id }
            for (name in current.edits.tagNamesToSend()) {
                val key = name.trim().lowercase()
                if (key.isEmpty() || key in ids) continue
                when (val created = catalog.createTag(name.trim(), key = UUID.randomUUID())) {
                    is ApiResult.Ok -> ids[key] = created.value.id
                    is ApiResult.Err -> {
                        state.update { it.copy(confirming = false, confirmError = created.error) }
                        return@launch
                    }
                }
            }
            when (val result = imports.confirm(previewId, current.edits.request(ids))) {
                is ApiResult.Ok -> state.update { it.copy(confirming = false, result = result.value) }
                is ApiResult.Err -> state.update {
                    it.copy(
                        confirming = false,
                        expired = result.error.isExpired(),
                        confirmError = result.error.takeUnless { e -> e.isExpired() },
                    )
                }
            }
        }
    }

    /** Leaving without importing drops the preview on the server. */
    fun discard() {
        val previewId = id ?: return
        if (state.value.result != null || state.value.confirming) return
        viewModelScope.launch { imports.discard(previewId) }
    }
}

data class BrokerUiState(
    val preview: BrokerPreview? = null,
    val expired: Boolean = false,
    val filter: BrokerFilter = BrokerFilter.ALL,
    val excluded: Set<String> = emptySet(),
    val choices: Map<String, TickerChoice> = emptyMap(),
    val confirming: Boolean = false,
    val error: ApiError? = null,
    val result: BrokerImportResult? = null,
) {
    val importing: Int
        get() = preview?.operations.orEmpty().count { it.status == BrokerOperationStatus.NEW && it.key !in excluded }
}

/** The preview of the Freedom Finance reports (docs/04 §13.3); it lives in memory only. */
@HiltViewModel
class BrokerPreviewViewModel @Inject constructor(private val imports: ImportsRepository) : ViewModel() {

    private val state = MutableStateFlow(BrokerUiState())
    val uiState: StateFlow<BrokerUiState> = state

    fun load(id: String) {
        if (state.value.preview?.id == id || state.value.expired) return
        val preview = imports.brokerPreview(id)
        state.update { if (preview == null) it.copy(expired = true) else it.copy(preview = preview) }
    }

    fun setFilter(filter: BrokerFilter) = state.update { it.copy(filter = filter) }

    fun include(key: String, include: Boolean) = state.update {
        it.copy(excluded = if (include) it.excluded - key else it.excluded + key)
    }

    fun includeAll(include: Boolean) = state.update { s ->
        val fresh = s.preview?.operations.orEmpty().filter { it.status == BrokerOperationStatus.NEW }
        s.copy(excluded = if (include) emptySet() else fresh.map { it.key }.toSet())
    }

    fun choose(ticker: String, choice: TickerChoice) = state.update {
        it.copy(
            choices = it.choices + (ticker to choice),
        )
    }

    fun confirm() {
        val current = state.value
        val preview = current.preview ?: return
        if (current.confirming || current.importing == 0) return
        state.update { it.copy(confirming = true, error = null) }
        viewModelScope.launch {
            val result = imports.confirmFreedom(
                preview.id,
                BrokerModel.choiceRequest(current.choices),
                current.excluded.toList(),
            )
            when (result) {
                is ApiResult.Ok -> state.update { it.copy(confirming = false, result = result.value) }
                is ApiResult.Err -> state.update {
                    it.copy(
                        confirming = false,
                        expired = result.error.isExpired(),
                        error = result.error.takeUnless { e -> e.isExpired() },
                    )
                }
            }
        }
    }

    fun discard() {
        val current = state.value
        val preview = current.preview ?: return
        if (current.result != null || current.confirming) return
        viewModelScope.launch { imports.discard(preview.id) }
    }
}
